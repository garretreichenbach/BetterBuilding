# BetterBuilding — AI Ship Generator: Full Plan

**The authoritative design + roadmap document.** Companion: [`PROGRESS.md`](./PROGRESS.md)
tracks live status and points here for rationale.

> **One-line goal:** turn a natural-language description ("a small armored fighter",
> "a boxy mining freighter") into a pasteable StarMade ship/template, in-game, via a
> local vision-language model — by adapting minebench's `voxel.exec` pattern with
> retrieval and a render→critique→repair loop.

---

## 1. Background & motivation

### 1.1 What minebench is (the thing we're adapting)
[minebench](https://github.com/Ammaar-Alam/minebench) benchmarks LLM spatial reasoning by
having models build Minecraft-style voxel structures from prompts. Its load-bearing idea —
the only part we copy — is **`voxel.exec`**:

- The model does **not** emit a list of blocks. It writes a **small program** that calls
  injected primitives (`block`, `box`, `line`, seeded `rng`). A model writes ~50 lines that
  draw thousands of blocks, sidestepping token limits.
- The program runs in a **locked-down sandbox** (no eval/fs/net, timeout, block caps); the
  primitive calls accumulate into a build.
- A **validation pass** expands/dedups/normalizes and applies **geometric quality gates**
  (min blocks, footprint span, height span).
- A **retry loop** re-prompts with the previous output **plus the specific error** on failure.
- Its thesis: *"the models that win are the ones that THINK the hardest before writing any
  code"* — single-shot, plan-then-one-program, aimed at capable models.

(minebench also has a human-voting Glicko-2 leaderboard — that's the "bench" half; irrelevant
to us as a generator.)

### 1.2 Why earlier attempts here failed
Prior iterations of this project tried WFC and LLM approaches and got nowhere. Root causes,
diagnosed from the earlier code:

1. **Models too weak.** Generation defaulted to small local models; voxel spatial reasoning
   humbles even frontier models. A 7B can't do it.
2. **Multi-phase, blind.** A 4-phase loop fed the model **text cross-sections** between phases
   — an LLM can't reconstruct 3D geometry from ASCII. Errors compounded.
3. **No quality gate.** The retry loop only caught code *exceptions*; a clean script producing
   a flat 6-block smear "succeeded."
4. **Orientation corruption.** The blueprint reader collapsed StarMade's 5-bit orientation to 6
   directions with a wrong heuristic, **and** used a 16³ segment size when the engine uses 32³ —
   so corpus data was garbage going in.
5. **Fine-tuning on the wrong signal.** Training decomposed blueprints with **fake captions**
   (filenames) and greedy-packing targets teaches memorization, not design; and the corpus is
   orders of magnitude too small to instill spatial reasoning that the base model lacks.

### 1.3 What changed (why this is now viable)
A stronger PC can run **Qwen3-VL-30B-A3B** locally via LM Studio. The decisive feature isn't
speed — it's **vision**. A VLM can *see* a render of the generated ship and critique it, which
unlocks the automated **render→critique→repair** loop minebench can't do, fully offline.

---

## 2. Design principles (settled decisions)

| Principle | Rationale |
|-----------|-----------|
| **Program, not block list.** Model writes Lua calling primitives. | minebench's core insight; beats token limits and yields structured, editable output. |
| **Single-shot global generation, not multi-phase tiling.** | Coherence (silhouette, symmetry, proportions) is *global*; local/phased generation is what failed before. |
| **Quality gates + repair, not just error-retry.** | Reject clean-but-bad output (flat, tiny, asymmetric) and re-prompt with the reason. |
| **Vision feedback is the key lever.** Render → VLM critiques image → repair. | The one thing minebench lacks; the biggest quality multiplier; now local. |
| **Retrieval over fine-tuning.** Corpus = in-context examples. | Fine-tuning can't install missing spatial reasoning; the corpus is far more valuable as retrieved exemplars. Fine-tune is a deferred fallback. |
| **Frontier API (if any) is a one-time offline tool.** | Used only for optional captioning/distillation; never a runtime dependency. Runtime is fully local. |
| **Storage granularity ≠ generation unit.** Keep `VoxelTemplate` segment-agnostic. | 32³ segments are an I/O detail the engine handles; coupling generation to them re-creates the WFC/local-coherence failure. |
| **Native CopyArea for I/O, no hand-rolled codec.** | The engine serializes type/orientation/connections/inventory losslessly. |
| **Preserve the raw 5-bit orientation byte end-to-end.** | Never remap orientation; that corrupted the old corpus. |

---

## 3. Architecture

### 3.1 Data flow (target, end state)
```
description
  → (retrieval) find k nearest captioned blueprints from the corpus index
  → buildPrompt(description, retrieved exemplars, palette, dims)
  → LM Studio (Qwen3-VL) emits Lua program
  → LuaExecutor (sandboxed) writes into VoxelTemplate
  → quality gates (block count, footprint/height span, symmetry, connectivity)
       ├─ fail → repair prompt (prev code + specific failure) → regenerate
       └─ pass → render to image(s)
  → VLM critique (sees the render): "nose not tapered, fin asymmetric, floating block at stern"
       ├─ flaws → repair prompt (critique) → regenerate/patch
       └─ good → VoxelTemplate → CopyArea → .smtpl → BuildToolsManager PASTE
```

### 3.2 Representation
- **`VoxelTemplate`** — dense 3D grid, `(short type, byte orientation)` per cell,
  `index = x + y*dx + z*dx*dy`. Convention: **X=width, Y=height (up), Z=length**, X-midpoint
  is the symmetry axis. Segment-agnostic.
- **`.smtpl`** — native StarMade serialization via `CopyArea`. Full fidelity.
- **Corpus index** — `{blueprint, real_caption, tags(role/size/style), decomposed_program?, dims, embedding}`.

### 3.3 Components (build order mirrors the phases)
```
videogoose.betterbuilding
  gen/
    VoxelTemplate     ✅ grid + toCopyArea/fromCopyArea + fill/set/clear
    TemplateStore     ✅ save/load .smtpl
    BlockPalette      ⬜ name↔id, shape families, orientation constants
    LuaExecutor       ⬜ sandboxed voxel.exec primitives writing into VoxelTemplate
    QualityGate       ⬜ geometric validators → pass/fail + reasons
    Renderer          ⬜ VoxelTemplate → image(s) for the VLM (and corpus captioning)
  ai/
    AIClient          ⬜ LM Studio OpenAI-compatible client (text + image content)
    PromptBuilder     ⬜ system/user/repair/critique prompts
    Generator         ⬜ orchestration: generate → gate → render → critique → repair loop
  corpus/
    BlueprintReader   ⬜ read .smd3 (32³ segments!) → VoxelTemplate; lossless orientation
    Captioner         ⬜ render + VLM caption → index rows
    RetrievalIndex    ⬜ embed + nearest-neighbor lookup
  command/
    GenTestCommand    ✅ /bb_gentest — Phase-0 paste proof (no LLM)
    GenerateCommand   ⬜ /bb_generate <description> — the real entry point
```

---

## 4. Roadmap (phases)

Each phase ships something runnable; quality climbs, never blocked on training.

### Phase 0 — Foundation (output path) — 🟡 in progress
**Goal:** prove code-built templates paste correctly in-game.
- ✅ `VoxelTemplate`, `TemplateStore`, `/bb_gentest`.
- ⬜ **Run `/bb_gentest` in-game.** Pass = correct size, correct chirality (notch top-front-right,
  fin on +X), blocks valid (not dead). Fix HP packing if blocks ghost (use 4-arg `makeDataInt`
  with full HP).
- **Exit criteria:** a synthetic asymmetric template round-trips `VoxelTemplate→.smtpl→paste`
  with correct geometry, orientation, and block validity.

### Phase 1 — Captioned, searchable corpus
**Goal:** turn 500–5000 blueprints into real (caption ↔ ship) data.
- ⬜ `BlockPalette` (name↔id, shape families, orientation constants) — also needed by the executor.
- ⬜ `BlueprintReader` reading **32³** segments, **lossless orientation**.
- ⬜ `Renderer` (orthographic views) — reused for captioning *and* the critique loop.
- ⬜ `Captioner`: render each blueprint → VLM writes structured caption (role, size class,
  silhouette, features, style). **Local VLM = ~$0.**
- ⬜ `RetrievalIndex`: embed captions, nearest-neighbor lookup.
- **Exit criteria:** query a description, get back relevant real ships with captions.

### Phase 2 — Retrieval-augmented generation + vision loop (the working generator)
**Goal:** `/bb_generate <description>` reliably produces recognizable ships.
- ⬜ `LuaExecutor` (voxel.exec core) — primitives write into `VoxelTemplate`; sandboxed; capped.
- ⬜ `AIClient` for LM Studio (text + image messages).
- ⬜ `PromptBuilder` — system (judging criteria, failure modes, API docs), user (description +
  retrieved exemplars + palette + dims), repair, critique.
- ⬜ `QualityGate` — geometric validators.
- ⬜ `Generator` — single-shot generate → gate-repair loop → render → VLM critique → repair.
- ⬜ `GenerateCommand` — `/bb_generate`, pastes the result.
- **Exit criteria:** for common prompts (fighter, freighter, station), output is recognizable
  and structurally sound a strong majority of the time.

### Phase 3 — Scaling & polish (as needed)
- ⬜ **Bilateral symmetry**: generate one half + mirror (cheap coherence-preserving scale lever).
- ⬜ **Coarse-to-fine for capital ships** (only if a ship exceeds context): global low-res blockout
  → tiled refinement conditioned on the global plan + neighbors. Segment-aligned tiles OK *under
  global conditioning*. **Never pure local tiling.**
- ⬜ **QLoRA distillation** (deferred, only if the base model proves insufficient): use Phase-2's
  validated outputs as teacher data — real captions + executor-validated programs, not greedy
  decompositions.

---

## 5. Technical reference (verified against StarMade.jar)

### 5.1 Block data int (`SegmentData`)
`type` 11 bits [0–10] · `hitpoints` 7 bits [11–17] · `active` 1 bit [18] · `orientation` 5 bits [19–23].
- Pack: `SegmentData.makeDataInt(short type, byte orient)` — **does not set HP**. If pasted
  blocks read as dead, use `makeDataInt(short type, byte orient, boolean active, byte hp)` with
  full HP (max 127 in 7 bits; use the block's real max from `ElementInformation`).
- `SEG = 32`, `BLOCK_COUNT = 32768` (32³). **The old 16³ reader was wrong.**

### 5.2 CopyArea recipe (build / save / load / paste)
```java
// build
CopyArea area = new CopyArea();
area.min = new Vector3i(0,0,0);
area.max = new Vector3i(dx-1, dy-1, dz-1);
VoidSegmentPiece p = new VoidSegmentPiece();
p.voidPos.set(x,y,z);
p.setDataByReference(SegmentData.makeDataInt(type, orient));
area.getPieces().add(p);                 // sparse: one piece per non-air cell

// save (engine writes ./templates/ only → move it)
area.save(name); Files.move("./templates/<name>.smtpl" → ourDir);
// load
CopyArea a = new CopyArea(); a.load(file);   // a.min/max may be (0,0,0) — recompute from pieces
// paste
BuildToolsManager btm = GameClient.getPICM().getBuildToolsManager();
btm.loadCopyArea(file);  // or btm.setCopyArea(area)
btm.setCopyPasteMode(CopyPasteMode.PASTE);
```
- Selected block: `GameClient.getPICM().getSelectedTypeWithSub()`, validate `ElementKeyMap.isValidType`.
- Read a placed block's orientation: `SegmentPiece.getOrientation()` — **keep the raw byte; never remap.**

### 5.3 Mod plumbing
- Command: implement `api.utils.game.chat.CommandInterface`; register in `onEnable` via
  `StarLoader.registerCommand(...)`.
- Chat commands run on the **server thread**; GUI/OpenGL must go through
  `StarLoaderTexture.runOnGraphicsThread(...)` (the paste path doesn't touch GL).
- Build/run: `./gradlew jar` → `<starmade_root>/mods/`. **Game + LM Studio are on a Windows PC**;
  copy the (pure-Java) jar there or build on Windows. Generated templates →
  `<starmade working dir>/BetterBuilding/templates/`.

### 5.4 LM Studio / Qwen3-VL-30B-A3B settings
- Variant: **30B-A3B** (MoE, ~3B active) — fast, tolerates CPU/GPU offload; ~Q4 ≈ 18–20 GB.
- Sampling (Qwen defaults for code): `temperature 0.7`, `top_p 0.8`, `top_k 20`, `min_p 0`.
- **Context ≥ 16k–32k** (system prompt + palette + exemplars + repair history add up).
- **Load the multimodal GGUF with the vision projector (mmproj)** — the critique loop needs
  image input via `image_url` content parts. Verify the quant repo ships vision, not text-only.
- `max_tokens` ≥ 2–4k (a full ship program is long; truncation causes spurious Lua errors).

---

## 6. Open questions / risks
- **HP packing** — does paste normalize HP, or do we need the 4-arg `makeDataInt`? (Phase 0 answers.)
- **Forward spatial reasoning ceiling** — how good is Qwen3-VL at *generating* coherent voxel
  programs (vs critiquing)? If weak, lean harder on retrieval exemplars and symmetry.
- **Render fidelity** — how faithful must the render be for the VLM critique to be useful?
  Cheap orthographic voxel render may suffice; engine screenshots are richer but harder to automate.
- **Orientation vocabulary** — StarMade's shape families (wedge/corner/tetra/hepta × 5-bit) are a
  large action space; may need to constrain or template common patterns.
- **Corpus licensing/provenance** — where the 500–5000 blueprints come from; fine for local use,
  flag before any redistribution.

---

## 7. Glossary
- **Segment** — StarMade's 32³ storage/streaming chunk. I/O detail, not a generation unit.
- **`.smtpl`** — native StarMade template (a saved `CopyArea` block region).
- **`voxel.exec`** — minebench's pattern: model writes a program calling building primitives.
- **VLM** — vision-language model (Qwen3-VL); can read images for the critique loop.
- **Quality gate** — geometric validator rejecting clean-but-bad builds and feeding the repair loop.
- **Coarse-to-fine** — global low-res plan first, then conditioned local refinement (the *correct*
  way to scale, vs pure tiling).
