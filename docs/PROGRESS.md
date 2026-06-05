# BetterBuilding — Progress / Resume Doc

A working checkpoint of what's built, how it works, what's verified, and where to pick up.
Companion to [PLAN_SUMMARY.md](../PLAN_SUMMARY.md) (the authoritative spec) and [README.md](../README.md) (the vision).

_Last updated: M0 + M1 complete and in-game verified; M2 in progress (palette editor GUI)._

---

## TL;DR status

| Milestone | State |
|-----------|-------|
| **M0 — Foundation** | ✅ Done, verified in-game (capture round-trips) |
| **M1 — Palettes & slots** | ✅ Done, verified in-game (shape-aware reskin works) |
| **M2 — Styles & content format** | 🟡 In progress — palette editor GUI built (testing); StyleLoader / authoring / reference pack not started |
| M3–M5 | Not started |

Committed baseline: `3a9482a "M0-1 done"`. The M2 palette-editor work (gui/, PaletteWriter, PaletteSub, inspect) is **uncommitted** on `main` at time of writing.

---

## How to build / run

- `./gradlew jar` → writes `BetterBuildingv2.0.0.jar` into `<starmade>/mods/`.
- StarMade root (build + runtime): `C:/Users/garre/OneDrive/Game Files/StarMade Files/Dev Build/` (set via `starmade_root` in `gradle.properties`).
- StarMade **API source** (for looking up signatures): `C:/Users/garre/OneDrive/Projects/StarMade-Master/src/main/java` (and `src/precompiled/java`). NOTE: the macOS path in README/PLAN is wrong for this machine.
- Content + config live under `<starmade>/moddata/BetterBuilding/` (see below). Reload the jar in-game (Mods menu) after a rebuild; palette JSON edits are read fresh per command (no reload needed).

---

## On-disk layout (runtime)

Everything is under the mod's resources folder `moddata/BetterBuilding/` (from `getSkeleton().getResourcesFolder()`), consolidated so config + content + logs sit together:

```
moddata/BetterBuilding/
  settings.yml          # composer_key (default MINUS), via StarMade FileConfiguration
  palettes/
    grey.json           # sample (grey armor)
    crystal.json        # sample (teal/blue/orange crystal armor) — distinct so reskin is visible
  templates/
    *.smtpl + *.json    # captured templates + sidecars
  styles/               # (empty — M2)
  logs/                 # mod logs
```

> Earlier the content root was `./BetterBuilding/` (StarMade root). It was moved to `moddata/BetterBuilding/` so it sits with the config the user expected. `BbFiles.init(getSkeleton().getResourcesFolder())` sets this on enable.

---

## Commands ( `/bb <sub>` )

| Command | Status | What it does |
|---------|--------|--------------|
| `/bb capture <name> [slot]` | ✅ works | Save current build-mode copy selection → `.smtpl` + sidecar. **Copy (Ctrl+C) a region first.** |
| `/bb reskin <palette> [buffer]` | ✅ works | Shape-aware re-skin of the copy selection. **In-place** by default (rewrites blocks where they sit, undoable); `buffer` mutates the copy buffer for paste. |
| `/bb inspect` | ✅ works | List distinct base blocks in the copy selection, flagged mapped/unmapped by palettes. Authoring aid. |
| `/bb palette <name>` | 🟡 testing | Open the click-to-assign palette editor GUI (see below). |
| `/bb reload` | ✅ works | Re-scan palettes/styles, report load errors. (Style loading is a stub until M2 StyleLoader.) |
| `/bb compose` | ⬜ stub | Placeholder for the M3 composer. |

Keybind: **`-` (MINUS)** opens the composer (M3) — currently a no-op scaffold. Rebindable via `composer_key` in `settings.yml`. Every letter A–Z is taken by vanilla StarMade; MINUS is the one free convenient key. There is **no** rebindable mod-keybind registry in StarMade, hence config-driven.

---

## Architecture / package map

```
videogoose.betterbuilding
  BetterBuilding.java       # entry: bootstrap folders, load config, install samples, register command + keybind
  SampleContent.java        # writes grey.json / crystal.json on first run (writeIfAbsent)
  command/                  # BbCommand dispatcher + SubCommand impls (capture, reskin, inspect, palette, reload, compose)
  content/                  # Palette (model+loader+resolver), PaletteLibrary (cross-palette index), PaletteWriter, TemplateSidecar
  slot/                     # Slot, SlotRegistry, Shape, SlotCell, ShapeInference, PaletteResolver
  io/                       # TemplateStore (.smtpl + sidecar via engine CopyArea)
  gui/                      # PaletteEditorDialog / Panel / State (click-to-assign editor)
  util/                     # BbFiles (paths), BbConfig (settings.yml), BuildAccess (build-mode accessors)
```

---

## Key technical findings (the load-bearing ones)

1. **`.smtpl` is a native StarMade format.** `CopyArea.save(name)` / `.load(file)` serialize a block region with full fidelity (type, orientation, connections, inventory). We did **not** hand-roll an `Smd3Codec` — `TemplateStore` delegates to the engine and adds the JSON sidecar. `CopyArea.save()` only writes to `./templates/`, so we save there then move the file.

2. **Shape-aware palette swap = StarMade's own FillTool logic.** A block's shape family lives on its **base/cube** block: `ElementInformation.styleIds` (shape variants) and `slabIds` (slabs), with `getSourceReference()` giving the base of any variant.
   - **Gotcha that bit us:** `styleIds`/`slabIds` are **compact arrays** (e.g. `[wedge, corner, tetra, hepta]`), **NOT** indexed by `blockStyle.id`. Indexing by `blockStyle.id` (WEDGE=1, CORNER=2, TETRA=4, HEPTA=5) scrambled shapes. **Fix:** `Palette.resolve()` *searches* the target's variant arrays for the entry whose actual `blockStyle`/`slab` matches the source (`findVariant`). Layout-independent and correct.

3. **In-place reskin uses `EditableSendableSegmentController.remove(...)`** with a `replaceFilterWith` + orientation — the atomic replace (same path as FillTool). `addElement` alone won't overwrite an occupied cell. Needs a `BuildRemoveCallback`, a `BuildInstruction`, and a `Set<Segment>` modded-set; commit with `pim.addToUndoStack(instr)`. Region bounds come from the copy area's `min`/`max`; blocks are read fresh from the world via `getSegmentBuffer().getPointUnsave(pos)`.

4. **GUI must run on the graphics thread.** Chat commands execute on the **server thread** (`PacketCSAdminCommand.processPacketOnServer`); creating a `GUIInputDialog` makes OpenGL calls and throws "No OpenGL context" off-thread. Wrap dialog creation in `StarLoaderTexture.runOnGraphicsThread(...)`.

5. **Block name lookup is locale-sensitive.** `ElementKeyMap.getInfoByName` matches the *translated* `getName()`. `Palette.lookup` falls back to the untranslated config name (scanning `getInfoArray()`) then numeric id, so palettes are stable across locales. Palettes are written with `getNameUntranslated()`.

6. **Build-mode access chain:** `GameClient.getPICM()` / `GameClient.getClientState()....getBuildToolsManager()`. Selected hotbar block: `getPICM().getSelectedTypeWithSub()`. Controlled entity: `getPICM().getSegmentControlManager().getSegmentController()` (returns `EditableSendableSegmentController`).

---

## Palette editor GUI (M2, in testing)

- `/bb palette <name>` → `PaletteSub` sets `PaletteEditorState.current` (slot→block draft, preloaded from existing palette) then activates `PaletteEditorDialog` **on the graphics thread**.
- Decision: **click-to-assign**, not drag-drop. The mod-facing `ItemSlot` widget is a non-functional prototype; the real `InventoryPanelNew` is coupled to `InventoryControlManager` (no standalone limited-slot inventory). Click-to-assign uses the proven `GUIInputDialogPanel` + `SimpleGUIVerticalButtonPane` + `GUITextOverlay` pattern.
- Current iteration is **text-based** (Assign buttons + live status list). **Next iteration:** swap in block-icon slots via `GUIBlockSprite(state, type)`; revisit drag-drop later.
- **Open test question:** does the dialog reliably open/render and do clicks update the status list? (Graphics-thread fix just applied.)

---

## What's left in M2

- [ ] **StyleLoader** — parse a style folder (`style.json` + palettes + `templates/`), resolve references, surface errors. Lock the draft schemas in PLAN_SUMMARY §1.
- [ ] **`/bb reload`** upgrade — actually load styles into memory + list them (currently just counts files).
- [ ] **Reference style pack** — ship one worked-example style folder.
- [ ] **In-game template authoring** — select region → tag slot + per-face sockets → save into a style's `templates/`. (User chose GUI; will follow the palette-editor pattern.)
- [ ] Palette editor: block-icon slots (and eventually drag-drop).

## Future ideas (captured in agent memory)

- Palette-authoring **drag-drop inventory UI** (labeled slots, copy-not-consume) — the eventual upgrade to click-to-assign.
- **Glowing/emissive blocks** in palettes (Bastyn crystals, glowing motherboard) for dramatic reskins.
- **Advanced Build Tools menu integration** via a mixin (`GUIAdvTool`) so BB tools live in the native build menu (`StarMod.getMixinConfigs()`).

---

## Gotchas for next session

- Rebuild + **reload the jar in the Mods menu**; palette JSON changes don't need a reload but code does.
- Test reskin in **creative** — in-place replace consumes the target block from inventory in survival (engine gates on `checkAllPlace`).
- `reskin`/`inspect`/`palette` all read the **copy buffer** — Ctrl+C a region first.
- Old LLM/WFC code is gone from `main` but lives in git history (`22fec5b^`); the project pivoted to the template compositor.
