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

### Orientation (will matter from Phase 1 on)
StarMade uses a 5-bit orientation (0–31): 6 basic directions plus rotated variants, and shape
families (wedge/corner/tetra/hepta) interpret it differently. The earlier `BlueprintReader`
**corrupted** this by collapsing 5-bit → 6 directions. For the rebuild: keep the raw orientation
byte end-to-end (capture via `SegmentPiece.getOrientation()`, write via `makeDataInt`), never
remap it. The native CopyArea path already preserves it losslessly.

---

## Current code map

```
videogoose.betterbuilding
  BetterBuilding.java          # onEnable → StarLoader.registerCommand(new GenTestCommand())
  gen/
    VoxelTemplate.java         # dense grid (short type, byte orient); toCopyArea/fromCopyArea; fill/set/clear
    TemplateStore.java         # save .smtpl (engine save + move), load
  command/
    GenTestCommand.java        # /bb_gentest — Phase-0 paste test, no LLM
```

## Next up
- [ ] Run `/bb_gentest` in-game; confirm geometry + chirality + block validity. Fix HP packing if needed.
- [ ] Then Phase 1: `BlockPalette` (name↔id, orientation constants) + blueprint reading for the corpus.
- [ ] Then `LuaExecutor` (the voxel.exec core) writing into `VoxelTemplate`, + `AIClient` (LM Studio settings: temp 0.7 / top_p 0.8 / top_k 20, 16k+ ctx, multimodal mmproj loaded).
```
