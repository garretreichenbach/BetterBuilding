# BetterBuilding — AI Ship Generator (fresh start)

Resume doc for the rebuilt project: a description → StarMade ship/template generator,
adapting minebench's `voxel.exec` pattern (LLM writes code calling building
primitives, sandboxed, with a quality-gated repair loop). Runtime model:
**Qwen3-VL-30B-A3B**, local via **LM Studio** on a separate Windows PC.

> **See [`PLAN.md`](./PLAN.md) for the authoritative design + roadmap + rationale.**
> This file is live status only.

_Last updated: Phase 0 foundation slice written + compiles; awaiting in-game paste test._

---

## The plan (phases)

| Phase | Goal | State |
|-------|------|-------|
| **0 — Foundation** | Prove the output path: code-built `VoxelTemplate` → `CopyArea` → `.smtpl` → paste, with correct geometry/orientation in-game. | 🟡 written, compiles; needs in-game test |
| 1 — Captioned corpus | Render the 500–5000 blueprint corpus, caption locally with the VLM → searchable (caption ↔ ship) index. | ⬜ |
| 2 — RAG generation | Retrieval-augmented single-shot Lua generation + render→critique→repair loop + quality gates. | ⬜ |
| 3 — Custom model (deferred) | QLoRA distill only if Phase 2 proves the base model insufficient. | ⬜ |

Design decisions already settled:
- **Retrieval over fine-tuning.** The corpus is most valuable as in-context examples, not SFT targets (fake captions + scale + capability-vs-style make naive SFT fail). Fine-tuning is Phase 3, only if needed.
- **Single-shot program, not multi-phase.** The earlier 4-phase approach fed the model text cross-sections it can't spatially parse. Generate one program; repair on quality-gate failure.
- **Vision feedback is the key lever** minebench lacks: render the result, let the VLM critique the image, repair.
- The frontier API (if used at all) is a **one-time offline tool** (captioning/distillation), never a runtime dependency.

---

## Build / run / test

- Code on Mac: `/Users/garret/Documents/GitHub/BetterBuilding`. StarMade.jar + libs present here, so it **compiles/typechecks on the Mac**.
- **Game + LM Studio run on the Windows PC.** Workflow: build the jar, get it to the Windows `<starmade>/mods/`, reload in the in-game Mods menu.
  - `./gradlew jar` → writes `BetterBuildingv2.0.0.jar` into the `starmade_root`/mods (set in `gradle.properties`; Mac path active, Windows path commented).
  - To build for Windows: either build on Windows with the Windows `starmade_root`, or copy the Mac-built jar to the Windows mods folder (it's pure Java, no native bits).
- Generated templates are written to `<starmade working dir>/BetterBuilding/templates/*.smtpl`.

### Phase 0 test (`/bb_gentest`)
Select any block in your hotbar, enter build mode, run `/bb_gentest`. It builds a
synthetic **asymmetric** shape in code and loads it into PASTE mode:
- 9×7×14 solid hull, a 3×3×3 notch carved from the **top-front-right (+X/+Y/+Z)** corner,
  and a tall thin fin standing above the **+X** edge near the back (low Z).
- **Pass criteria:** pasted shape has correct size, the notch and fin on the expected
  sides (chirality correct, not mirrored), blocks intact (not dead/zero-HP/ghosted).
- If chirality is flipped → axis/orientation convention bug. If blocks are dead/invalid
  → HP packing needs `makeDataInt` with hitpoints (see below).

---

## Load-bearing API recipe (recovered from git history + verified against StarMade.jar)

The output path is the engine's native `CopyArea`; **do not hand-roll a segment codec.**

- **Build a template in code** (`VoxelTemplate.toCopyArea()`):
  ```java
  CopyArea area = new CopyArea();
  area.min = new Vector3i(0,0,0);
  area.max = new Vector3i(dx-1, dy-1, dz-1);
  // per non-air cell:
  VoidSegmentPiece p = new VoidSegmentPiece();
  p.voidPos.set(x, y, z);
  p.setDataByReference(SegmentData.makeDataInt(type, orientation)); // (short, byte)
  area.getPieces().add(p);
  ```
  `pieces` is sparse (one entry per block). `CopyArea` packing matches `index = x + y*dx + z*dx*dy`.
- **HP caveat:** `makeDataInt(short,byte)` does not set hitpoints. If pasted blocks read as
  invalid, switch to `makeDataInt(short type, byte orient, boolean active, byte hp)` (4-arg)
  with full HP. Confirm empirically in Phase 0.
- **Save/load** (`TemplateStore`): `CopyArea.save(name)` only writes to engine `./templates/`;
  save there then `Files.move` to our folder. Load via `new CopyArea(); area.load(file)`.
- **Loaded bounds gotcha:** `area.min/max` are sometimes (0,0,0) after `load()`. `fromCopyArea`
  recomputes the bounding box from the pieces and prefers it when stored bounds are too small.
- **Paste:** `BuildToolsManager btm = GameClient.getPICM().getBuildToolsManager();`
  `btm.loadCopyArea(file)` (or `btm.setCopyArea(area)` in-memory) then
  `btm.setCopyPasteMode(CopyPasteMode.PASTE)`.
- **Selected block:** `GameClient.getPICM().getSelectedTypeWithSub()`; validate with `ElementKeyMap.isValidType(type)`.
- **Commands:** implement `api.utils.game.chat.CommandInterface`; register in `onEnable` via
  `StarLoader.registerCommand(new XxxCommand())`. Chat commands run on the **server thread**
  (GUI/OpenGL work must be wrapped in `StarLoaderTexture.runOnGraphicsThread`; the paste path here does not touch GL).

### Orientation
5-bit orientation (0–31): 6 basic directions plus rotated variants; shape families
(wedge/corner/tetra/hepta) interpret it differently. The earlier reader **corrupted** this by
collapsing 5-bit → 6. Rule: keep the raw orientation byte end-to-end (`SegmentPiece.getOrientation()`
to read, `setOrientation(byte)` to write); never remap. The native CopyArea path preserves it losslessly.

### Blueprint corpus on disk + reader (Phase 1)
- Corpus: `<root>/blueprints/<name>/` — main `DATA/*.smd3` region files (`<name>.<rx>.<ry>.<rz>.smd3`) +
  `ATTACHED_N/` docked turrets, plus `.smbph`/`.smbpl`/`.smbpm` metadata. ~37 of ~147 have real main-hull
  data; many others are WIP/junk/turrets. Skip macOS `._*` AppleDouble files.
- `.smd3` is StarMade's hierarchical **`Tag`** format (NOT raw int arrays — another old-reader bug).
- **Engine reader stack** (for the future headless path): `SegmentRegionFileNew` (region file + `SegmentHeader`
  index of present segments) → `SegmentDataIONew.requestStatic(...)` → `RemoteSegment.deserialize` →
  `SegmentData4Byte`. Catch: `RemoteSegment` needs a live `SegmentController` + segment provider, so there is
  no trivial file→blocks call — hence spawn-and-read first.
- **Chosen approach — spawn-and-read:** load a blueprint in-game (catalog UI, or future `BluePrintController`
  auto-spawn) → capture its `SegmentController` via `CopyArea.copyArea(sc, getMinPos(), getMaxPos())` →
  `VoxelTemplate.fromCopyArea`. `Vector3iSegment extends Vector3i`. Dense capture capped at ~64M cells
  (capital titans exceed it → future sparse/headless reader).

---

## Current code map

```
videogoose.betterbuilding
  BetterBuilding.java          # onEnable → register GenTestCommand + CaptureCommand
  gen/
    VoxelTemplate.java         # dense grid (short type, byte orient); toCopyArea (4-byte setters) / fromCopyArea
    TemplateStore.java         # save .smtpl (engine save + move), load
    BlueprintReader.java       # SegmentController → VoxelTemplate via CopyArea.copyArea (seg coords ×SEG!)
    LuaExecutor.java           # sandboxed LuaJ voxel.exec; ~30 primitives → VoxelTemplate (adapted from git)
    BlockPalette.java          # hotbar blocks → Lua NAME→id map (robust; user controls palette)
    TemplateGenerator.java     # description → LLM Lua → execute → gate → render→VLM-critique → repair loop
    QualityGate.java           # geometric checks (min blocks, span, flatness, near-solid-box) → issues list
    render/
      VoxelView.java           # engine-free read interface for the renderer
      Renderer.java            # pure-Java isometric voxel rasterizer → BufferedImage (validated headless)
      BlockColors.java         # type id → RGB by averaging the block's texture tile; runtime + cached
      VoxelTemplateView.java   # VoxelTemplate + BlockColors → VoxelView
  ai/
    AIConfig.java              # loads ./BetterBuilding/ai.properties (base_url/model/embed_model/...), writes defaults
    AIClient.java              # OpenAI-compatible LM Studio: captionImage (vision) + embed (/embeddings)
    RetrievalIndex.java        # embed captions → index.json; cosine query; lenient caption JSON parse
  command/
    GenTestCommand.java        # /bb_gentest — Phase-0 paste test, no LLM
    CaptureCommand.java        # /bb_capture [name] — read build-mode entity → .smtpl, loads PASTE mode
    RenderCommand.java         # /bb_render [name] — ship → isometric PNG (build-mode entity, or captured .smtpl)
    CaptionCommand.java        # /bb_caption [name] — render → Qwen3-VL → structured JSON caption (bg thread)
    IndexCommand.java          # /bb_index — embed all captions → BetterBuilding/index.json
    SearchCommand.java         # /bb_search <desc> [k] — embed query → cosine top-k captioned ships
    GenerateCommand.java       # /bb_generate <desc> [WxHxL] — generate ship → save → PASTE mode
```

LuaJ is already in StarMade `lib/` (luaj-jse-3.0.jar) — no dep to add. LuaExecutor adapted from
git (`22fec5b^`) via sed (package + TemplateMetaData→VoxelTemplate); VoxelTemplate got alias setters
(setTypeAt/setOrientationAt/getTypeAt/getOrientationAt). Generator is single-shot + Lua-error/empty
repair; AI-chosen dims (cap 128); palette from hotbar; orient.NAME → executor → 4-byte packing.

LM Studio: `./BetterBuilding/ai.properties` (base_url=http://100.97.5.58:1234/v1, model=qwen/qwen3-vl-30b,
**embed_model**=text-embedding-nomic-embed-text-v1.5 — user must load an embedding model + set this).
gson-2.1 is in StarMade `lib/` (compile + in-game runtime; not bundled). gson 2.1 quirk: `JsonArray.add`
only takes `JsonElement` → wrap primitives (`new JsonPrimitive(f)`).
Corpus pipeline (proven): /bb_capture → /bb_caption → /bb_index → /bb_search.

**Paste size limit:** `server.cfg` → `PLAYER_MAX_BUILD_AREA` (default 100) is the **per-axis** bounding-box
cap on build selections AND template pastes (`CopyArea.build` checks `getSizef()` axes vs
`getMaxBuildArea()` ← `ServerConfig.PLAYER_MAX_BUILD_AREA`). Not a block count. Raise it for bigger
ships; keep it ≥ `TemplateGenerator.MAX_DIM` (128) or large generations fail to paste.

Renderer notes: isometric 2:1 projection, painter's algorithm by depth=x+y+z, surface-culls interior
voxels, per-face shading (top/+X/+Z). Colours = average of each block's texture tile (engine atlas:
sheets `t###.png`, 16×16 tiles @64px; sheet=texId/256, tileX=texId%256%16, tileY=texId%256/16; uses
`getTextureId(0)`, alpha-weighted average), computed at runtime → never stale. Self-tested headless:
synthetic fighter render + atlas swatch dump both look correct. Single iso view confirmed sufficient
(VLM captioned a real destroyer accurately). Future: edge outlines.

**Known limitation — cube-only:** the renderer draws every block as a full cube; it ignores block
shape (wedge/corner/tetra/hepta/slab) and the orientation byte (the data IS retained in VoxelTemplate,
just unused). Heavily-shaped hulls look blockier than reality. Fine for VLM-level legibility
(role/silhouette/proportion). Deferred — and intentionally: do NOT decode StarMade's esoteric 5-bit
orientation math. When needed, reuse the engine's shape geometry (`BlockShapeAlgorithm` / per-shape mesh
vertex tables) → look up (shape, orientation) → partial-cube polygons fed to the same projection code.
Same limitation will apply to the Phase-2 critique-loop render (VLM sees silhouette, not fine shaping).

## Next up
- [ ] **Re-run `/bb_gentest` with a WEDGE selected** — original test used orientation 0 + low-id block, so it
      could not validate the 4-byte orientation/type packing fix. Confirm wedges face correctly + aren't corrupt.
- [ ] **Test `/bb_capture`**: spawn a real blueprint (e.g. "ASD Proto-Javelin"), enter build mode, capture →
      confirm block count + dims look right, and the saved `.smtpl` pastes back identically.
- [x] `/bb_caption` proven: Qwen3-VL captioned a real destroyer accurately (role/size/features/palette).
- [ ] Load an embedding model in LM Studio + set `embed_model`; run `/bb_index` then `/bb_search` to
      confirm retrieval surfaces sensible ships.
- [ ] Curate: capture + caption a set of good ships, build the index over them.
- [x] Phase 2 v1 (Track A): `LuaExecutor` + `BlockPalette` + `TemplateGenerator` + `/bb_generate` (single-shot).
- [x] Quality gate + render→VLM-critique→repair loop wired into the generator (MAX_ATTEMPTS=6).
      Flow per attempt: LLM Lua → execute → (Lua-error/0-block repair) → QualityGate repair → VLM critique
      repair → accept; best-effort fallback if attempts exhausted. Gate runs before the VLM call.
- [x] Critique made **scored 0-10** (not binary): accept at >=7, keep BEST attempt, stop on 2-round plateau,
      anti-stall (identical Lua → temp +0.15; +0.05 per repair; "make substantial changes" instruction);
      feed back top 3 issues only. Fixes the "never accepts / block count frozen" stall.
- [x] Vision-in-the-loop: repair attempts send a render of the model's own build (AIClient.chatVision);
      prompt pushes smooth()/hollow() finishing. Lua robustness: lenient optInt/optBool (coerce bool↔num in
      optional slots, fixes cone hollow/tip_r crash), Lua errors quote the exact offending source line, and a
      WORKED EXAMPLE is in the prompt (teaches correct arg counts).
- [ ] Test `/bb_generate` again — watch for scores rising + early stop. Tune ACCEPT_SCORE/thresholds/prompt.
- [x] Retrieval priming: generation queries index.json (top-2) → injects reference-ship captions into the
      prompt (best-effort; unprimed if no index/embed). Prompt pushes shape variety (not all ellipsoid) + ≥3
      colours; QualityGate flags monochrome (distinct types < 2); critic penalizes blob/monochrome.
- [x] Reference IMAGES (not just captions) sent on the initial attempt (chatVisionMulti loads renders/<name>.png);
      worked example now built from the ACTUAL palette names (fixes model copying placeholder blocks.GLASS/HULL).
- [ ] To activate retrieval: caption several ships (/bb_caption) → /bb_index (needs embed model) → /bb_generate.
- [ ] Palette = hotbar: for colourful ships the user must stock varied blocks (+ glass) in the hotbar.
- [ ] More refinement as needed: shape-aware rendering (BlockShapeAlgorithm), decompose captured ships to
      structural/Lua exemplars (stronger than caption-only priming), symmetry enforcement, per-region critique.
- [ ] Deferred: headless `/bb_capture_all <pattern>` (requestStatic, no spawn; coord math needs in-game check);
      shape-aware rendering (reuse `BlockShapeAlgorithm`); `BlockPalette` for the executor.
```
