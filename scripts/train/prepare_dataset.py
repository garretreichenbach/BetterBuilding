"""
Prepare BetterBuilding training data for Unsloth fine-tuning.

The /bb_export_training command produces JSONL where each line is one
conversation in plain three-role chat format:

    {"messages": [
        {"role": "system",    "content": "..."},
        {"role": "user",      "content": "..."},
        {"role": "assistant", "content": "<tool_call>\\n{\"name\":\"fill\",...}\\n</tool_call>"},
        {"role": "user",      "content": "<tool_response>\\nFilled 12 blocks\\n</tool_response>"},
        ... repeats ...
    ]}

Tool calls are embedded as text inside assistant messages and tool results
come back as user messages, because Gemma's chat template only knows
user/model roles. The wire format strings (`<tool_call>` etc.) are defined in
`ToolCallFormat.java` on the Java side and must stay in lock step.

This module:
  1. Loads one or more such JSONL files into a Hugging Face Dataset.
  2. Validates each conversation looks plausible.
  3. Optionally produces a deterministic train/eval split.
  4. When run as a script, writes the cleaned dataset back out so you can
     inspect it before training.

Usage as a script:
    python prepare_dataset.py \\
        --input ../../bb_training.jsonl \\
        --out-dir ./data \\
        --eval-ratio 0.05

Usage as a library:
    from prepare_dataset import load_dataset_from_jsonl
    ds = load_dataset_from_jsonl(["bb_training.jsonl"])
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Iterable, List, Sequence

from datasets import Dataset, DatasetDict


VALID_ROLES = {"system", "user", "assistant"}


# ---------------------------------------------------------------------------
# Loading + validation
# ---------------------------------------------------------------------------


def _validate_conversation(messages: Sequence[dict], src: str) -> None:
    if not messages:
        raise ValueError(f"{src}: empty messages array")
    if messages[0].get("role") != "system":
        raise ValueError(f"{src}: first message must be system, got {messages[0].get('role')}")
    saw_assistant = False
    for i, msg in enumerate(messages):
        role = msg.get("role")
        if role not in VALID_ROLES:
            raise ValueError(f"{src}: message {i} has unsupported role '{role}' "
                             f"(only {sorted(VALID_ROLES)} are allowed in the new format)")
        if "content" not in msg or msg["content"] is None:
            raise ValueError(f"{src}: message {i} ({role}) is missing content")
        if not isinstance(msg["content"], str):
            raise ValueError(f"{src}: message {i} ({role}) content must be a string, "
                             f"got {type(msg['content']).__name__}")
        if "tool_calls" in msg:
            raise ValueError(f"{src}: message {i} contains a 'tool_calls' field — this "
                             f"file appears to be in the old structured-tool-calls format. "
                             f"Re-export it with the current /bb_export_training command.")
        if role == "assistant":
            saw_assistant = True
    if not saw_assistant:
        raise ValueError(f"{src}: no assistant turns to train on")


def _iter_jsonl(path: Path) -> Iterable[dict]:
    with path.open("r", encoding="utf-8") as fh:
        for line_no, raw in enumerate(fh, start=1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError as e:
                raise ValueError(f"{path}:{line_no}: invalid JSON: {e}") from e
            if "messages" not in obj:
                raise ValueError(f"{path}:{line_no}: missing 'messages' key")
            messages = obj["messages"]
            _validate_conversation(messages, f"{path}:{line_no}")
            # Strip any unexpected keys; keep only role+content.
            cleaned = [{"role": m["role"], "content": m["content"]} for m in messages]
            yield {"messages": cleaned}


def load_dataset_from_jsonl(paths: Sequence[str | os.PathLike]) -> Dataset:
    """Load and validate one or more JSONL files into a single Dataset."""
    rows: List[dict] = []
    for p in paths:
        path = Path(p)
        if not path.is_file():
            raise FileNotFoundError(f"Training file not found: {path}")
        rows.extend(_iter_jsonl(path))
    if not rows:
        raise ValueError("No training examples loaded")
    return Dataset.from_list(rows)


def split_train_eval(ds: Dataset, eval_ratio: float, seed: int = 42) -> DatasetDict:
    if eval_ratio <= 0:
        return DatasetDict({"train": ds})
    if eval_ratio >= 1:
        raise ValueError("eval_ratio must be in [0, 1)")
    split = ds.train_test_split(test_size=eval_ratio, seed=seed)
    return DatasetDict({"train": split["train"], "eval": split["test"]})


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Prepare BetterBuilding training data")
    parser.add_argument(
        "--input", "-i",
        nargs="+",
        required=True,
        help="One or more JSONL files exported by /bb_export_training",
    )
    parser.add_argument(
        "--out-dir", "-o",
        default="./data",
        help="Directory to write the cleaned dataset into (default: ./data)",
    )
    parser.add_argument(
        "--eval-ratio",
        type=float,
        default=0.05,
        help="Fraction of examples held out for eval (default: 0.05). Use 0 to disable.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for the train/eval split",
    )
    parser.add_argument(
        "--format",
        choices=("jsonl", "parquet"),
        default="jsonl",
        help="Output format (default: jsonl)",
    )
    args = parser.parse_args(argv)

    ds = load_dataset_from_jsonl(args.input)
    print(f"Loaded {len(ds)} conversations from {len(args.input)} file(s).")

    splits = split_train_eval(ds, args.eval_ratio, seed=args.seed)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    for name, subset in splits.items():
        target = out_dir / f"{name}.{args.format}"
        if args.format == "jsonl":
            subset.to_json(str(target), force_ascii=False)
        else:
            subset.to_parquet(str(target))
        print(f"  {name}: {len(subset)} -> {target}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
