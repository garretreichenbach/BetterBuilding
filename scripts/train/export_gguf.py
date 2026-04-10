"""
Merge a LoRA adapter trained by train_unsloth.py into its base model and
export to GGUF for use in LM Studio / llama.cpp / Ollama.

The BetterBuilding mod talks to a local LLM via LMStudioClient
(`/v1/chat/completions`), so the practical end goal of training is a GGUF
loaded into LM Studio.

Examples:
    # Save merged 16-bit weights only (no GGUF conversion)
    python export_gguf.py \
        --adapter ./out/bb-qwen25c-7b-lora \
        --merged-out ./out/bb-qwen25c-7b-merged

    # Convert to GGUF in q4_k_m and q8_0 (default Unsloth method)
    python export_gguf.py \
        --adapter ./out/bb-qwen25c-7b-lora \
        --gguf-out ./out/bb-qwen25c-7b-gguf \
        --quantization q4_k_m q8_0
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from unsloth import FastLanguageModel  # type: ignore


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Merge LoRA + export to GGUF")
    p.add_argument("--adapter", required=True,
                   help="Path to the LoRA output directory from train_unsloth.py")
    p.add_argument("--base-model",
                   help="Override the base model id (defaults to whatever the "
                        "adapter was trained on, recorded in adapter_config.json)")
    p.add_argument("--max-seq-length", type=int, default=8192)
    p.add_argument("--load-in-4bit", action="store_true", default=False,
                   help="Load base in 4-bit while merging (slower but lower VRAM)")

    p.add_argument("--merged-out",
                   help="If set, save merged 16-bit safetensors here")
    p.add_argument("--gguf-out",
                   help="If set, save GGUF conversion(s) here")
    p.add_argument("--quantization", nargs="+",
                   default=["q4_k_m"],
                   help="GGUF quant methods (e.g. q4_k_m q5_k_m q8_0 f16)")
    return p.parse_args(argv)


def _resolve_base_model(adapter_dir: Path, override: str | None) -> str:
    if override:
        return override
    cfg = adapter_dir / "adapter_config.json"
    if not cfg.is_file():
        raise FileNotFoundError(
            f"Cannot find {cfg}. Pass --base-model to specify it explicitly."
        )
    import json
    data = json.loads(cfg.read_text())
    base = data.get("base_model_name_or_path")
    if not base:
        raise ValueError(
            f"{cfg} does not contain base_model_name_or_path. "
            f"Pass --base-model explicitly."
        )
    return base


def main() -> int:
    args = parse_args()
    if not args.merged_out and not args.gguf_out:
        print("Nothing to do: pass --merged-out and/or --gguf-out", file=sys.stderr)
        return 2

    adapter_dir = Path(args.adapter).resolve()
    if not adapter_dir.is_dir():
        raise SystemExit(f"Adapter directory not found: {adapter_dir}")

    base_model = _resolve_base_model(adapter_dir, args.base_model)
    print(f"Loading base model: {base_model}")
    print(f"Loading adapter:    {adapter_dir}")

    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=str(adapter_dir),
        max_seq_length=args.max_seq_length,
        load_in_4bit=args.load_in_4bit,
        dtype=None,
    )

    if args.merged_out:
        out = Path(args.merged_out)
        out.mkdir(parents=True, exist_ok=True)
        print(f"Merging LoRA -> 16-bit safetensors: {out}")
        model.save_pretrained_merged(
            str(out),
            tokenizer,
            save_method="merged_16bit",
        )

    if args.gguf_out:
        out = Path(args.gguf_out)
        out.mkdir(parents=True, exist_ok=True)
        for q in args.quantization:
            print(f"Exporting GGUF [{q}] -> {out}")
            model.save_pretrained_gguf(
                str(out),
                tokenizer,
                quantization_method=q,
            )

    print("\nDone.")
    if args.gguf_out:
        print(f"Load the GGUF in LM Studio from: {Path(args.gguf_out).resolve()}")
        print("Then point the BetterBuilding mod's API URL at LM Studio's "
              "OpenAI endpoint (default http://localhost:1234/v1).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
