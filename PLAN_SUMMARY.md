# BetterBuilding — Plan Summary

Authoritative reference extracted from README.md and docs/implementation-plan.md.
Do not edit by hand — regenerate from those source documents if they change.

---

## 1. Data Models

### 1.1 SlotCell *(draft)*
Represents a single cell in the composer's slot-space grid.

| Field       | Type    | Description                                              |
|-------------|---------|----------------------------------------------------------|
| slot        | Slot    | Named material role (e.g. PRIMARY_HULL, ACCENT, GLASS). Registry-based, not a fixed enum — styles can declare custom slots (e.g. GREEBLE_FINE). |
| shape       | Shape   | Fixed geometry family: CUBE, WEDGE, CORNER, TETRA, HEPTA, PENTA, SLAB_* |
| orientation | byte    | StarMade 5-bit orientation value, preserved verbatim     |

A composed structure is stored as a sparse 3D grid: `Map<Vec3i, SlotCell>` (type alias: `StructureGrid`).

### 1.2 PaletteResolver (interface) *(draft)*

```java
interface PaletteResolver {
    short resolve(Slot slot, Shape shape);
    // returns concrete StarMade block type, or -1 if slot has no entry
}
```

### 1.3 Palette file — palette.*.json *(draft)*

Top-level fields:

| Field | JSON type | Description                              |
|-------|-----------|------------------------------------------|
| name  | string    | Human-readable palette name              |
| slots | object    | Map of slot name → entry object          |

Each entry object:

| Field | JSON type | Description                                                  |
|-------|-----------|--------------------------------------------------------------|
| block | string    | Base block name (resolved via ElementKeyMap); shape family expanded automatically from ElementInformation.styleIds / slabIds |

Example (`palette.crystal.json`):
```jsonc
{
  "name": "Crystal",
  "slots": {
    "PRIMARY_HULL":   { "block": "CRYSTAL_ARMOR" },
    "SECONDARY_HULL": { "block": "GREY_HULL" },
    "ACCENT":         { "block": "BLUE_ARMOR" },
    "GLASS":          { "block": "GLASS_BLOCK" },
    "LIGHT":          { "block": "WHITE_LIGHT_BAR" },
    "SYSTEM":         { "block": "POWER_REACTOR" }
  }
}
```

Built-in slot names: PRIMARY_HULL, SECONDARY_HULL, ACCENT, GLASS, LIGHT, SYSTEM.
Styles may declare additional custom slots under the `slots` key of style.json.

### 1.4 Template sidecar — templates/*.json *(draft)*
Accompanies each `.smtpl` file. Template payload stores slot-space cells (palette-independent).

| Field  | JSON type       | Description                                       |
|--------|-----------------|---------------------------------------------------|
| slot   | string          | Module type this template fills (e.g. "ROOM")     |
| size   | [int, int, int] | Bounding box [x, y, z]                            |
| sockets| array of object | Connection points (see below)                     |
| anchor | [int, int, int] | Origin cell for placement                         |
| tags   | array of string | Arbitrary classifier tags (e.g. "interior")       |

Each socket object:

| Field | JSON type | Description                                          |
|-------|-----------|------------------------------------------------------|
| face  | string    | Which face: Z_POS, Z_NEG, X_POS, X_NEG, Y_POS, Y_NEG |
| type  | string    | Connection type (e.g. "CORRIDOR") — must match adjacency rules in style.json |

### 1.5 Style file — style.json *(draft)*

| Field          | JSON type | Description                                              |
|----------------|-----------|----------------------------------------------------------|
| name           | string    | Human-readable style name                                |
| archetypes     | array     | Valid structure archetypes: STATION, SHIP, OUTPOST, DERELICT |
| defaultPalette | string    | Filename of the default palette                          |
| slots          | object    | Optional custom slot declarations (key = slot name)      |
| layers         | object    | Map of layer name → layer config (hull, floor, corridor, room, …) |
| rules          | object    | adjacency (socket compatibility) and stacking rules      |
| superstructure | object    | Per-attachment-type config (bridge, engine, …)           |
| script         | string    | Optional Lua script filename (e.g. "rules.lua")          |

Layer config:

| Field     | JSON type | Description                           |
|-----------|-----------|---------------------------------------|
| templates | array     | List of template names for this layer |
| cadence   | int       | (optional) Repetition cadence         |

Stacking rules object:

| Field        | JSON type | Description                          |
|--------------|-----------|--------------------------------------|
| deckHeightMin| int       | Minimum allowed deck height          |
| deckHeightMax| int       | Maximum allowed deck height          |
| shaftAlign   | boolean   | Whether vertical shafts must align   |

Superstructure entry:

| Field     | JSON type | Description                           |
|-----------|-----------|---------------------------------------|
| templates | array     | Template names for this attachment    |
| attach    | string    | Attachment anchor name (e.g. TOP_CENTER, REAR_FACE) |

### 1.6 .smd3 bit layout (for Smd3Codec)

| Bits  | Field       |
|-------|-------------|
| 0–10  | TYPE (block type) |
| 11–17 | HP (hit points) |
| 18    | ACTIVE (active state) |
| 19–23 | ORIENT (5-bit orientation) |

---

## 2. Package / Directory Layout

### 2.1 Java package tree

```
videogoose.betterbuilding
  BetterBuilding.java          # StarMod entry point; registers commands, keybind,
                               #   drawer; loads content on enable
  command/                     # CommandInterface implementations (/bb compose,
                               #   /bb author, /bb reload, /bb capture, /bb reskin)
  gui/                         # Composer panels, authoring dialogs
  compose/                     # ComposerSession + phase state machine
      phase/                   # Phase implementations: Footprint, Decks, Style,
                               #   Superstructure, Palette, Refine
      model/                   # Value objects: Footprint, DeckStack,
                               #   StructureGraph, ModulePlacement
  content/                     # Style, Template, Palette models + loaders (JSON/Lua)
  slot/                        # Slot registry, Shape enum, PaletteResolver
  render/                      # GhostDrawer (wraps ModWorldDrawer); block-grid → preview
  io/                          # Smd3Codec (read/write .smd3), TemplateStore,
                               #   BlueprintStore
  author/                      # In-game region capture + slot/socket tagging
  util/                        # Math helpers (grid, AABB, orientation),
                               #   seeded RNG
```

### 2.2 On-disk content folder (under StarMade install dir)

```
BetterBuilding/
  styles/
    <style-name>/
      style.json               # Grammar: slots, templates, adjacency/stacking rules,
                               #   superstructure rules
      palette.default.json     # Default slot → block mapping (shape-aware)
      rules.lua                # OPTIONAL procedural rules
      templates/
        *.smtpl                # StarMade-native block region files
        *.json                 # Per-template sidecar metadata
  palettes/
    *.json                     # Standalone palettes usable across styles
  templates/                   # Global template store (captured via /bb capture)
```

### 2.3 Build output

The Gradle build writes the jar directly into `<starmade_root>/mods/`. The `starmade_root` property is set in `gradle.properties`.

---

## 3. StarLoader / API Touchpoints

All verified against the StarMade source at `/Users/garret/Documents/GitHub/StarMade`.

| Need                    | API class / member                                                                 | Used by / notes                                            |
|-------------------------|------------------------------------------------------------------------------------|------------------------------------------------------------|
| Mod entry               | `api.mod.StarMod` (`onEnable`)                                                     | `BetterBuilding.java` — already extended                   |
| Command registration    | `api.utils.game.chat.CommandInterface`, `StarLoader.getCommand` / registration     | `command/` — `/bb compose`, `/bb author`, `/bb reload`, `/bb capture`, `/bb reskin` |
| Event listener reg.     | `StarLoader.registerListener`                                                      | `BetterBuilding.onEnable`                                  |
| Keybind / input         | `api.listener.events.input.KeyPressEvent`                                          | Open composer (default key `-` / MINUS); footprint cell sketching  |
| Mouse input             | `api.listener.events.input.MousePressEvent`                                        | Footprint sketching                                        |
| GUI panels              | `api.utils.gui.GUIMenuPanel`                                                       | Main composer panels                                       |
| GUI input dialog        | `api.utils.gui.GUIInputDialog`                                                     | Authoring dialogs                                          |
| GUI button pane         | `api.utils.gui.SimpleGUIVerticalButtonPane`                                        | Phase navigation                                           |
| GUI popup               | `api.utils.gui.SimplePopup`                                                        | Errors, confirmations                                      |
| Block name ↔ ID lookup  | `api.utils.element.Blocks`, `Blocks.fromId`, `api.utils.element.ElementKeyMap`     | Palette loader — resolve block name to type ID             |
| Shape family expansion  | `api.utils.element.ElementInformation` (`.styleIds`, `.slabIds`)                  | `slot/PaletteResolver` — expand base block to wedge/corner/tetra/hepta/penta/slab |
| Block placement         | `api.utils.element.BlocksWriter`                                                   | `compose/` export — commit resolved grid into SegmentController |
| Target entity           | `api.utils.game.SegmentControllerUtils`                                            | Identify target entity for block paste                     |
| Holographic preview     | `api.utils.draw.ModWorldDrawer` (`.update`, `.draw`, `.postWorldDraw`)             | `render/GhostDrawer` — render ghost each frame             |
| Template / blueprint IO | `org.schema…world.SegmentData` (`.smd3` bit-packing)                              | `io/Smd3Codec` — reuse decode logic from prior BlueprintReader |
| Lua scripting           | `org.luaj` (bundled with StarMade)                                                 | `content/` style loader — sandboxed `rules.lua` execution  |

---

## 4. Milestones and Acceptance Criteria

### Milestone 0 — Foundation
Goal: the mod loads, registers a command + keybind, and can round-trip a region of blocks to/from disk.

Tasks:
- `BetterBuilding.onEnable`: register `/bb` command group and keybind listener.
- Config + folder bootstrap: create `BetterBuilding/{styles,palettes,templates}` under the StarMade dir on first run.
- `io/Smd3Codec`: decode and encode `.smd3` segment data (reconstruct bit-packing). Unit-test round-trip on a known segment.
- `io/TemplateStore`: read/write a `.smtpl` region + JSON sidecar.
- `/bb capture <name>`: save the current build-mode selection to a template (proves IO end-to-end).

Acceptance criteria: `/bb capture test` writes a file; reloading and pasting it reproduces the blocks exactly (including orientation).

---

### Milestone 1 — Palettes & Slots
Goal: re-skin an existing selection through the slot abstraction.

Tasks:
- `slot/Slot` registry + `slot/Shape` family enum.
- `content/Palette` model + JSON loader; validate against block names via `ElementKeyMap`.
- `slot/PaletteResolver`: expand base block → shape family using `ElementInformation.styleIds`/`slabIds`; nearest-shape fallback.
- Shape inference: given a concrete block, derive its `Shape` (reverse lookup over families) so a hand-built selection can be lifted into slot space.
- `/bb reskin <palette>`: map current selection → slot space → resolve through palette → write back.

Acceptance criteria: a hand-built grey-armor hull selection re-skins to crystal with wedges/corners staying correct.

---

### Milestone 2 — Styles & Content Format
Goal: load community content and author templates in-game.

Tasks:
- Finalize `style.json` / palette / template-sidecar schemas (lock the drafts in section 2 of implementation-plan.md).
- `content/StyleLoader`: parse a style folder; resolve template + palette references; surface load errors clearly.
- `author/`: in-game flow — select region → tag `slot` + per-face `sockets` → save into a chosen style's `templates/`.
- Ship one reference style + two palettes as a worked example/content-pack template.
- `/bb reload`: hot-reload all styles/palettes.

Acceptance criteria: author 3 corridor templates + 1 room in-game, save into a style, `/bb reload`, and see them listed in the composer's style picker.

---

### Milestone 3 — The Composer (vertical slice)
Goal: the end-to-end flow for a single-deck box, no superstructure.

Tasks:
- `compose/ComposerSession` + phase state machine (re-entrant, non-destructive).
- Footprint phase: `KeyPress`/`MousePress`-driven cell sketching with X/Z mirror.
- Decks phase: add/remove decks, per-deck height.
- Style assembly: fill footprint+decks with style layers (hull skin, floor, corridors, rooms) honoring adjacency sockets → produces a `StructureGrid` in slot space.
- `render/GhostDrawer`: render the palette-resolved grid as a ghost via `ModWorldDrawer`.
- Export: creative paste (`BlocksWriter`) or save as blueprint/template.

Acceptance criteria: sketch a footprint, add 2 decks, pick a style + palette, see a live ghost, and paste a coherent walled, corridored, skinned structure.

---

### Milestone 4 — Superstructure & Refinement
Goal: the parts that make output non-repetitive and directable.

Tasks:
- Superstructure placement (bridges/hangars/engines/turret hardpoints/antennae) at style-legal attach points.
- Per-module re-roll / lock / swap.
- Seeded generation (`util` RNG): same seed + inputs ⇒ identical structure; surface the seed in the UI.

Acceptance criteria: add a bridge + engines, re-roll one room while a locked room stays put, and reproduce an identical build from a saved seed.

---

### Milestone 5 — Polish & Sharing
Goal: procedural rules, packaging, browsing.

Tasks:
- Lua rule hook (`rules.lua`) with a sandboxed placement callback API.
- Content-pack packaging (zip in/out) + an in-menu style/palette browser with thumbnails.
- Survival build-guide hologram (assemble-against ghost) as an export mode.
- Docs: authoring guide for the community.

Acceptance criteria: install a zipped third-party style pack, build with it, and a `rules.lua` "every 3rd panel gets a window" rule visibly takes effect.

---

## 5. Schema Status: Draft vs Stable

| Schema / Artifact                    | Status   | Notes                                                                          |
|--------------------------------------|----------|--------------------------------------------------------------------------------|
| `SlotCell` (java model)              | **draft**| Section 2 of implementation-plan.md explicitly labels the entire Core Data Model section *(draft)* |
| `palette.*.json` format              | **draft**| Labeled draft in implementation-plan.md §2.2                                  |
| `PaletteResolver` interface          | **draft**| Part of draft data model section                                               |
| Template sidecar `.json` format      | **draft**| Labeled draft in implementation-plan.md §2.3                                  |
| `style.json` format                  | **draft**| Labeled draft in implementation-plan.md §2.4; explicitly noted as "a design deliverable of Milestone 2" in README |
| `.smd3` bit layout                   | **stable**| Described as "from the prior code" — existing decoded format, not designed fresh |
| StarLoader API touchpoints table     | **stable**| "Verified against the source" per implementation-plan.md §3                   |
| Package / directory layout           | **stable**| Defined in both README and implementation-plan.md without draft qualifier      |
| Milestone task lists                 | **stable**| Defined acceptance criteria; tasks are checklists but the criteria are fixed   |

Key note from the implementation plan:
> "Schemas marked *(draft)* are expected to change as Milestones 1–2 validate them against the real API."

Milestone 2's acceptance criteria include explicitly locking all draft schemas: "Finalize `style.json` / palette / template-sidecar schemas (lock the drafts in §2)."
