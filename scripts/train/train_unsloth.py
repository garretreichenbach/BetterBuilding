"""
Fine-tune Gemma (or any small chat model) on BetterBuilding training data using Unsloth.

The training corpus is the JSONL exported by `/bb_export_training`. Each line
is one conversation in plain three-role chat format:

    system  -> task instructions + tool docs (text)
    user    -> build description + block palette
    assistant -> "<tool_call>{...}</tool_call>"
    user    -> "<tool_response>...</tool_response>"
    ... repeats ...
    assistant -> "<tool_call>{\"name\":\"finish\",...}</tool_call>"
    user    -> final tool response

We train on the assistant turns only — system and user are masked from the
loss so the model learns *which* tools to call, not how to imitate the user
prompt or fake tool outputs.

Defaults are tuned to fit Gemma 4 E4B in 4-bit on a 12 GB card (RTX 3060).
For larger models / cards, raise --max-seq-length and --per-device-batch-size.

Example:
    python train_unsloth.py \\
        --train-file ../../bb_training.jsonl \\
        --output-dir ./out/bb-gemma4-e4b-lora \\
        --epochs 3
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

# Unsloth must be imported before transformers/trl so its patches apply.
from unsloth import FastLanguageModel, is_bfloat16_supported  # type: ignore
from unsloth.chat_templates import train_on_responses_only  # type: ignore

import torch
from trl import SFTConfig, SFTTrainer

# Local helper
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
from prepare_dataset import load_dataset_from_jsonl, split_train_eval  # noqa: E402


# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

# Unsloth ships a dynamic 4-bit bitsandbytes build of Gemma 4 E4B specifically
# for fine-tuning. Do NOT use the `-GGUF` repo here — that's a llama.cpp
# inference artifact and isn't loadable by `FastLanguageModel.from_pretrained`.
# If you want to train in full precision instead, pass
# `--base-model unsloth/gemma-4-E4B-it --no-4bit` (much more VRAM).
DEFAULT_BASE_MODEL = "unsloth/gemma-4-E2B-it-unsloth-bnb-4bit"

# Gemma's chat template uses <start_of_turn>user / <start_of_turn>model markers.
# These are what `train_on_responses_only` keys off of to mask user/system
# tokens from the loss. Override only if you switch base model families.
DEFAULT_INSTRUCTION_PART = "<start_of_turn>user\n"
DEFAULT_RESPONSE_PART = "<start_of_turn>model\n"


# ---------------------------------------------------------------------------


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Fine-tune an LLM on BetterBuilding data with Unsloth")

    # Data
    p.add_argument("--train-file", nargs="+", required=True,
                   help="JSONL file(s) exported by /bb_export_training")
    p.add_argument("--eval-ratio", type=float, default=0.05,
                   help="Fraction of examples held out for eval (0 to disable)")
    p.add_argument("--seed", type=int, default=42)

    # Model
    p.add_argument("--base-model", default=DEFAULT_BASE_MODEL,
                   help=f"HF or Unsloth model id (default: {DEFAULT_BASE_MODEL})")
    p.add_argument("--max-seq-length", type=int, default=4096,
                   help="Max sequence length. Lower this if you OOM on the 3060.")
    p.add_argument("--load-in-4bit", action="store_true", default=True,
                   help="Use 4-bit quantization for the base model (default on)")
    p.add_argument("--no-4bit", dest="load_in_4bit", action="store_false")

    # LoRA
    p.add_argument("--lora-rank", type=int, default=16,
                   help="LoRA rank. 16 is conservative for a 3060; bump to 32 on bigger cards.")
    p.add_argument("--lora-alpha", type=int, default=16)
    p.add_argument("--lora-dropout", type=float, default=0.0)
    p.add_argument("--target-modules", nargs="+",
                   default=["q_proj", "k_proj", "v_proj", "o_proj",
                            "gate_proj", "up_proj", "down_proj"])

    # Training
    p.add_argument("--output-dir", default="./out/bb-finetune",
                   help="Where adapter weights and checkpoints are written")
    p.add_argument("--epochs", type=float, default=3.0)
    p.add_argument("--max-steps", type=int, default=-1,
                   help="If > 0, overrides --epochs")
    p.add_argument("--per-device-batch-size", type=int, default=1)
    p.add_argument("--grad-accum", type=int, default=8)
    p.add_argument("--learning-rate", type=float, default=2e-4)
    p.add_argument("--warmup-ratio", type=float, default=0.05)
    p.add_argument("--weight-decay", type=float, default=0.01)
    p.add_argument("--logging-steps", type=int, default=5)
    p.add_argument("--save-steps", type=int, default=200)
    p.add_argument("--eval-steps", type=int, default=200)

    # Response-masking instruction/response markers. Defaults are Gemma's;
    # override if you switch base models to Llama / Qwen / Mistral.
    p.add_argument("--instruction-template", default=DEFAULT_INSTRUCTION_PART)
    p.add_argument("--response-template", default=DEFAULT_RESPONSE_PART)

    p.add_argument("--resume", action="store_true",
                   help="Resume from latest checkpoint in --output-dir if present")

    return p.parse_args(argv)


# ---------------------------------------------------------------------------


def format_with_chat_template(examples: dict, tokenizer) -> dict:
    """Apply the tokenizer's chat template to each conversation."""
    texts = []
    for messages in examples["messages"]:
        text = tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=False,
        )
        texts.append(text)
    return {"text": texts}


def main() -> int:
    args = parse_args()

    print(f"Loading base model: {args.base_model}")
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=args.base_model,
        max_seq_length=args.max_seq_length,
        load_in_4bit=args.load_in_4bit,
        dtype=None,  # Let Unsloth pick bf16/fp16 based on the GPU
    )

    # Sanity check: render a tiny conversation through the chat template and
    # confirm both the user prompt and our <tool_call> wrapper survive.
    sample = [
        {"role": "system", "content": "You build templates."},
        {"role": "user", "content": "Build it."},
        {"role": "assistant",
         "content": "<tool_call>\n{\"name\": \"fill\", \"arguments\": {}}\n</tool_call>"},
        {"role": "user",
         "content": "<tool_response>\nFilled 0 blocks\n</tool_response>"},
    ]
    try:
        rendered = tokenizer.apply_chat_template(sample, tokenize=False, add_generation_prompt=False)
    except Exception as e:
        raise SystemExit(
            f"\nThe tokenizer for {args.base_model} failed to render a basic chat "
            f"({type(e).__name__}: {e}).\nIs the model id correct? Pass --base-model to override."
        )
    if "tool_call" not in rendered or "tool_response" not in rendered:
        print("WARNING: rendered template does not contain '<tool_call>' / '<tool_response>'. "
              "The chat template may be stripping them. Inspect this output before training:\n"
              + rendered[:1000])

    print("Attaching LoRA adapters...")
    model = FastLanguageModel.get_peft_model(
        model,
        r=args.lora_rank,
        target_modules=args.target_modules,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=args.seed,
        use_rslora=False,
        loftq_config=None,
    )

    # ---- Dataset ----
    print(f"Loading training data from: {args.train_file}")
    raw_ds = load_dataset_from_jsonl(args.train_file)
    splits = split_train_eval(raw_ds, args.eval_ratio, seed=args.seed)
    print(f"  train: {len(splits['train'])}"
          + (f"   eval: {len(splits['eval'])}" if "eval" in splits else ""))

    formatted = splits.map(
        lambda ex: format_with_chat_template(ex, tokenizer),
        batched=True,
        remove_columns=["messages"],
        desc="Applying chat template",
    )
    train_ds = formatted["train"]
    eval_ds = formatted.get("eval")

    # ---- Trainer ----
    bf16 = is_bfloat16_supported()
    sft_config = SFTConfig(
        output_dir=args.output_dir,
        per_device_train_batch_size=args.per_device_batch_size,
        per_device_eval_batch_size=args.per_device_batch_size,
        gradient_accumulation_steps=args.grad_accum,
        warmup_ratio=args.warmup_ratio,
        num_train_epochs=args.epochs if args.max_steps <= 0 else 1,
        max_steps=args.max_steps,
        learning_rate=args.learning_rate,
        weight_decay=args.weight_decay,
        bf16=bf16,
        fp16=not bf16,
        logging_steps=args.logging_steps,
        save_steps=args.save_steps,
        save_strategy="steps",
        eval_strategy="steps" if eval_ds is not None else "no",
        eval_steps=args.eval_steps if eval_ds is not None else None,
        optim="adamw_8bit",
        lr_scheduler_type="cosine",
        seed=args.seed,
        report_to="none",
        max_seq_length=args.max_seq_length,
        dataset_text_field="text",
        packing=False,
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=train_ds,
        eval_dataset=eval_ds,
        args=sft_config,
    )

    # Mask everything except assistant turns from the loss.
    trainer = train_on_responses_only(
        trainer,
        instruction_part=args.instruction_template,
        response_part=args.response_template,
    )

    # ---- Train ----
    gpu_stats = torch.cuda.get_device_properties(0)
    start_mem_gb = round(torch.cuda.max_memory_reserved() / 1024 ** 3, 2)
    max_mem_gb = round(gpu_stats.total_memory / 1024 ** 3, 2)
    print(f"GPU: {gpu_stats.name}  ({max_mem_gb} GB total, {start_mem_gb} GB reserved)")

    resume = args.resume and any(Path(args.output_dir).glob("checkpoint-*"))
    train_result = trainer.train(resume_from_checkpoint=resume if resume else None)

    end_mem_gb = round(torch.cuda.max_memory_reserved() / 1024 ** 3, 2)
    print(f"Peak GPU memory: {end_mem_gb} GB")
    print(f"Final loss: {train_result.training_loss:.4f}")

    # ---- Save adapters + tokenizer ----
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(out_dir))
    tokenizer.save_pretrained(str(out_dir))
    print(f"Saved LoRA adapter and tokenizer to {out_dir}")
    print("\nNext step: run export_gguf.py to merge and convert for Ollama / llama.cpp.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
