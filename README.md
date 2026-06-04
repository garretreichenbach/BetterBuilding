# BetterBuilding

> A composing / architect mod for **StarMade** — design elaborate ships and stations in minutes, then skin them in your own style.

BetterBuilding is being reworked into a **structure composer** in the spirit of [The Mighty Architect](https://github.com/simibubi/TheMightyArchitect) (and its [Architectury port](https://github.com/TimStewartJ/TheMightyArchitectury)), adapted from medieval houses to spaceships and space stations.

Instead of placing every block by hand, you sketch a footprint, stack decks, pick a **style**, and let BetterBuilding assemble a coherent structure out of modular **templates**. You then choose a **palette** to decide which actual blocks it's built from. Everything that drives the composer — templates, styles, and palettes — is plain, shareable content you can author and edit yourself.

It is **fully clientside**, so it works on **Vanilla or Modded** servers. Nothing here changes block logic or requires server install; the composer only produces ordinary blueprints/templates you place in build mode.

The StarMade API source is in /Users/garret/Documents/GitHub/StarMade

---

## Vision

The Mighty Architect's pitch is *"design elaborate buildings within a minute."* StarMade has the same problem in a different shape: greebling a hull, laying out interior decks, and keeping a consistent aesthetic across a fleet is slow, repetitive work. BetterBuilding aims to:

- Turn the tedious 80% of a build (hull paneling, corridors, repeated rooms, symmetric greebling) into a guided, seconds-long flow.
- Keep the creative 20% in the player's hands — the composer fills gaps; it doesn't replace signature design.
- Make **every** part of the pipeline user-authorable. No hardcoded block lists. Your styles, your templates, your palettes — drop a folder in, share it, remix someone else's.

---

## Core Concepts

The Architect's vocabulary maps cleanly onto StarMade once you swap "house" for "ship/station":

| Architect term | BetterBuilding term | What it is |
|---|---|---|
| Design | **Template** | A small, saved block region authored by a user — a corridor segment, a room, a hull panel, an engine nacelle, a bridge, a greeble. Tagged with a *slot* and connection *sockets*. Stored in StarMade's native template/blueprint format. |
| Theme | **Style** | A grammar + ruleset that bundles a set of templates with adjacency/stacking rules and a default palette. Defines *how* a structure assembles (corridor flow, deck stacking, hull greebling cadence, where superstructure attaches). |
| Palette | **Palette** | A mapping from abstract material *slots* (primary hull, secondary hull, accent, trim, glass, light, systems) to concrete StarMade block IDs — shape-aware, so a swap remaps every wedge/corner/tetra/hepta/penta/slab variant correctly. |
| Ground plan | **Footprint** | The deck outline you sketch in build mode. |
| Stories | **Decks** | Vertically stacked floors, each with adjustable height. |
| Roofs / towers | **Superstructure** | The StarMade analog: bridges, hangars/docking bays, engine blocks & nacelles, turret hardpoints, antennae, wings, sensor towers. |

**Slots** are the abstraction that makes palettes swappable. A template is authored using placeholder "slot" blocks (e.g. `PRIMARY_HULL`, `ACCENT`, `GLASS`). A palette resolves each slot to a real block *and its shape family*, so the same template can be rendered as grey advanced armor, crystalline hull, or rusted derelict plating without re-authoring.

---

## The Composer Workflow

A single guided flow, each phase non-destructive and re-editable. Bind a key (default proposed: `B`) or run `/bb compose` to open it.

1. **Pick a structure archetype** — Station, Ship, Outpost, or Derelict. Archetype sets sensible defaults (gravity orientation, whether systems/power get stubbed in, scale).
2. **Footprint** — Sketch the deck outline in build mode (drag a region or trace cells on a grid). Symmetry and mirroring toggles keep it clean.
3. **Decks** — Scroll to add/remove decks and drag to set each deck's height. The composer keeps vertical circulation (lifts/shafts) aligned.
4. **Style** — Choose a Style. The footprint + decks instantly populate with that style's templates: walls, floors, corridors, interior rooms, hull skin.
5. **Superstructure** — Add and position bridges, hangars, engines, turret hardpoints, antennae. The style constrains where these can legally attach.
6. **Palette** — Choose a Palette to skin the whole structure. Swap freely; preview updates live. Shape-aware remapping keeps smooth hull intact.
7. **Refine** — Per-module **re-roll** (regenerate one room/panel), **lock** (protect a module from re-rolls), and **swap** (pick a specific template for a slot).
8. **Preview** — A holographic ghost of the structure renders in-world (via the mod's world drawer) before anything is committed.
9. **Export / Build**
   - **Creative:** paste instantly into the world or save directly as a blueprint.
   - **Survival:** emit a build-guide hologram you assemble against, or save a template to print with normal build tools.

Nothing is committed until step 9 — every earlier phase is freely revisitable.

---

## Feature Set

### Composition
- Footprint sketching with symmetry (X/Z mirror, radial) and grid snapping.
- Multi-deck stacking with per-deck height and aligned vertical circulation.
- Style-driven auto-assembly of walls, floors, corridors, rooms, and hull skin.
- Superstructure placement (bridges, hangars, engines, turret hardpoints, antennae, wings) with style-legal attachment points.
- Per-module re-roll / lock / swap for fine control without leaving the flow.
- Seeded generation — same seed + inputs ⇒ same structure, for reproducible/shareable results.

### Palettes (skinning)
- Abstract material slots decoupled from concrete blocks.
- **Shape-aware remapping**: one palette entry per material drives the full shape family (cube, wedge, corner, tetra, hepta, penta, slab) so hull silhouettes survive a re-skin.
- Live re-skin of an already-composed structure — try grey armor, crystal, derelict, faction colors in seconds.
- Palettes work on *any* structure built with compatible slots, including hand-built ships you import.

### User content & sharing
- **Author templates in-game**: select a region, tag its slot and sockets, save it into a style.
- **Define styles & palettes as plain files** (JSON for declarative rules; optional Lua scripting for procedural/conditional rules — StarMade already ships LuaJ).
- **Content packs**: a style is a self-contained folder of templates + rules + default palette. Drop it in `BetterBuilding/styles/`, restart, done. Zip and share.
- Ships with a couple of reference styles/palettes as worked examples; the goal is community-made packs.

### Quality-of-life building tools
- (Carried over / planned) symmetry painting, region fill/replace, shape-aware hull smoothing (auto-wedge/corner edges), and palette-swap on existing selections — usable standalone, outside the full composer.

### Integration
- Native StarMade blueprint & template formats in and out — no proprietary file lockin.
- Import an existing blueprint and re-skin or extend it with the composer.
- Fully clientside; safe on any server.

---

## User Content Format (planned)

A style is just a folder:

```
BetterBuilding/
  styles/
    industrial-station/
      style.json          # grammar: slots, templates, adjacency & stacking rules, superstructure rules
      palette.default.json # default slot → block mapping (shape-aware)
      rules.lua            # OPTIONAL procedural rules (e.g. "every 3rd panel gets a window")
      templates/
        corridor_straight.smtpl
        room_quarters.smtpl
        hull_panel_a.smtpl
        bridge_small.smtpl
        ...
  palettes/
    crystal.json           # standalone palettes usable across styles
    derelict.json
```

- `style.json` declares the material **slots**, lists templates with their **slot** + **socket** metadata, and the **rules** for how they connect/stack.
- `palette.*.json` maps each slot to a base block ID; the composer expands each to its shape family automatically.
- `.smtpl` template files are StarMade-native block regions exported from the in-game authoring tool.
- `rules.lua` is optional escape-hatch scripting for anything the declarative format can't express.

(Exact schema is a design deliverable of Milestone 2 below.)

---

## Technical Plan

Built on the StarLoader / `api.mod.StarMod` API the mod already extends. Key API surfaces this leans on:

- **Commands & GUI** — `api.utils.game.chat` (command registration) and `api.utils.gui` (`GUIMenuPanel`, input dialogs, vertical button panes) for the composer UI and authoring tools.
- **Block I/O** — `api.utils.element.Blocks` / `BlocksWriter` and `ElementKeyMap`/`ElementInformation` for reading block metadata and resolving shape families (`styleIds`, `slabIds`) — this is what makes shape-aware palettes possible.
- **Holographic preview** — `api.utils.draw.ModWorldDrawer` to render the ghost structure before commit.
- **Input** — `KeyPressEvent` / `MousePressEvent` for footprint sketching and the keybind.
- **Blueprints/templates** — read/write StarMade's `.smd3` segment format (the prior `BlueprintReader` already decodes type/orientation/HP bit-packing — that decoding logic is reusable here for template import).
- **Scripting** — `org.luaj` (bundled with StarMade) for optional `rules.lua`.

### Proposed module layout
```
videogoose.betterbuilding
  ├─ compose/        # the workflow state machine (footprint → decks → style → ... → export)
  ├─ content/        # Style, Template, Palette models + loaders (JSON + Lua)
  ├─ slot/           # slot abstraction + shape-aware palette resolution
  ├─ render/         # holographic preview (ModWorldDrawer)
  ├─ author/         # in-game template authoring + tagging
  ├─ io/             # StarMade blueprint/template read & write
  ├─ gui/            # composer panels + authoring dialogs
  └─ command/        # /bb commands
```

---

## Roadmap

**Milestone 0 — Foundation**
- Command + keybind scaffolding; settings/config; folder layout under `BetterBuilding/`.
- Template I/O: read & write StarMade templates/blueprints (reuse existing `.smd3` decode).

**Milestone 1 — Palettes & slots**
- Slot abstraction + shape-aware resolution from `ElementInformation`.
- Palette file format + loader; re-skin an existing selection/blueprint as a standalone tool.

**Milestone 2 — Styles & content format**
- Finalize `style.json` schema (slots, templates, adjacency/stacking rules).
- Style loader; reference style + palette shipped as worked examples.
- In-game template authoring (select → tag slot/sockets → save into a style).

**Milestone 3 — The Composer**
- Footprint sketching → deck stacking → style assembly.
- Holographic preview via `ModWorldDrawer`.
- Export: creative paste / blueprint save / survival build-guide.

**Milestone 4 — Superstructure & refinement**
- Bridges/hangars/engines/turret hardpoints/antennae with legal attachment.
- Per-module re-roll / lock / swap; seeded reproducibility.

**Milestone 5 — Polish & sharing**
- Lua rule scripting hook; content-pack packaging; in-menu style/palette browser.

---

## Installation

Download from this page and drop the jar in your `/StarMade/mods` folder, or use the in-game downloader (main menu → **Mods → Browse**). Make sure the mod is marked **enabled** in the Mods menu before using it.

## Building from source

This project builds against a local StarMade install. Set `starmade_root` in `gradle.properties` to your StarMade directory, then:

```
./gradlew jar
```

The jar is written into your StarMade `mods/` folder automatically.

---

Happy building!
