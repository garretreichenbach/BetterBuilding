# Training BetterBuilding's AI with Unsloth

These scripts fine-tune a small chat model on the JSONL produced by
`/bb_export_training`, then export the result to GGUF so it can be loaded
into Ollama (which BetterBuilding talks to via the existing
`LMStudioClient` — Ollama exposes the same `/v1/chat/completions` endpoint).

The default target is **Gemma 4 E2B** in 4-bit, sized to fit on an 8 GB
RTX 3060. Bump to `unsloth/gemma-4-E4B-it-unsloth-bnb-4bit` via
`--base-model` if you have a 12 GB+ card.

## Wire format

Tool calls are embedded as plain text inside assistant messages instead of
using OpenAI's structured `tool_calls` field, because Gemma's chat template
only knows user/model roles. The exporter and the live inference loop
share the same wrapper strings (defined in `ToolCallFormat.java`):

```text
system    -> task instructions + ## TOOLS section
user      -> "Build a StarMade template ..."
assistant -> "<tool_call>\n{\"name\":\"fill\",\"arguments\":{...}}\n</tool_call>"
user      -> "<tool_response>\nFilled 216 blocks\n</tool_response>"
assistant -> "<tool_call>\n{\"name\":\"finish\",...}\n</tool_call>"
user      -> "<tool_response>\nTemplate finalized with 47 operations.\n</tool_response>"
```

If you ever change the wrapper strings, change them in **all four** places
or training and inference will silently disagree:

- `ToolCallFormat.java` (constants)
- `TemplateGenerator.java` (system prompt + parser)
- `TrainingDataExporter.java` (writes the JSONL)
- `train_unsloth.py` (sanity-check assertion)

## Pipeline

```
.smtpl / blueprints                                bb_training.jsonl
        │                                                 │
        ▼                                                 ▼
/bb_export_training  ───────────────────►  prepare_dataset.py  (optional)
                                                          │
                                                          ▼
                                                  train_unsloth.py
                                                          │
                                                          ▼
                                                  export_gguf.py
                                                          │
                                                          ▼
                                              Ollama  ◄──  BetterBuilding mod
```

## 1. Install

A CUDA GPU (Linux/Windows + NVIDIA) is required. From this directory:

```bash
python -m venv .venv
source .venv/bin/activate           # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
```

If `pip install unsloth` fails on your platform, follow the matrix at
<https://github.com/unslothai/unsloth#installation-instructions>.

## 2. Generate training data

In-game, run:

```
/bb_export_training -blueprints -o bb_training.jsonl
```

…or any subset, e.g. `/bb_export_training -blueprints "*fighter*"`.
The file lands in the StarMade root.

## 3. (Optional) Inspect / split the data

```bash
python prepare_dataset.py \
    --input ../../bb_training.jsonl \
    --out-dir ./data \
    --eval-ratio 0.05
```

This validates every record (each must use only system/user/assistant
roles, must contain at least one assistant turn, must not contain a
leftover `tool_calls` field from the old structured format) and writes
`data/train.jsonl` + `data/eval.jsonl`. You can skip this step —
`train_unsloth.py` does the same loading internally — but it is useful
for sanity-checking the exported corpus before burning GPU time.

## 4. Train

```bash
python train_unsloth.py \
    --train-file ../../bb_training.jsonl \
    --output-dir ./out/bb-gemma4-e4b-lora \
    --epochs 3
```

Notes:

- The default base model is `unsloth/gemma-4-E2B-it-unsloth-bnb-4bit`,
  Unsloth's dynamic 4-bit bnb build of Gemma 4 E2B-it, sized to fit on an
  8 GB 3060. Use `--base-model unsloth/gemma-4-E4B-it-unsloth-bnb-4bit`
  for the larger E4B variant on a 12 GB+ card. Do **not** point
  `--base-model` at any `-GGUF` repo — GGUF is a llama.cpp inference
  format and won't load via `FastLanguageModel.from_pretrained`. For
  full-precision training, pass `--base-model unsloth/gemma-4-E2B-it
  --no-4bit` (much more VRAM).
- Loss is computed on **assistant turns only** — system and user messages
  are masked via Unsloth's `train_on_responses_only` helper, keyed off
  Gemma's `<start_of_turn>user\n` / `<start_of_turn>model\n` markers.
- For a 12 GB 3060: defaults (`--max-seq-length 4096 --grad-accum 8
  --lora-rank 16`) are conservative. If you OOM, lower `--max-seq-length`
  to 2048 first; if you have headroom, raise it.
- For a rented 24 GB+ card: bump `--max-seq-length 8192 --lora-rank 32`
  and consider `--per-device-batch-size 2`.

## 5. Export to GGUF for Ollama

```bash
python export_gguf.py \
    --adapter ./out/bb-gemma4-e4b-lora \
    --gguf-out ./out/bb-gemma4-e4b-gguf \
    --quantization q4_k_m q8_0
```

Then create an Ollama Modelfile alongside the `.gguf` and import it:

```
FROM ./bb-gemma4-e4b-q4_k_m.gguf
TEMPLATE """{{ .System }}
{{ .Prompt }}"""
PARAMETER stop "<end_of_turn>"
```

```bash
ollama create bb-gemma4 -f Modelfile
ollama run bb-gemma4
```

(Use the chat template that ships with Gemma 4 in Ollama if it differs
from the snippet above — `ollama show gemma4:e4b --modelfile` will print
the upstream one to copy from.)

Then in `config.yml` for the BetterBuilding mod, set:

```yaml
provider: ollama
ollama-url: http://localhost:11434
ollama-model: bb-gemma4
```

You can also keep merged 16-bit weights with `--merged-out` if you'd
rather host the model under vLLM/TGI instead of llama.cpp.

## Files

| File                  | Purpose                                                              |
|-----------------------|----------------------------------------------------------------------|
| `requirements.txt`    | Python deps for the Unsloth stack                                    |
| `prepare_dataset.py`  | Loader + validator for the exported JSONL (also runnable as a CLI)   |
| `train_unsloth.py`    | Main fine-tuning script (LoRA, response-masked SFT)                  |
| `export_gguf.py`      | Merge LoRA + convert to GGUF for Ollama / llama.cpp                  |
