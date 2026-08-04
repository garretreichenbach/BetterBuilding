# BetterBuilding — In-World Annotation System

## Vision

Pivot BetterBuilding from a grab-bag of building tools into a **design-time annotation
layer** for StarMade: in-world drawables that label, measure, and mark up an entity the
way a 3D modeling package annotates a model.

The goal is to make *designing* easier, not building faster. A large ship is an
undocumented artifact — the person who built it knows where the power routing lives, and
nobody else does. Annotations turn the ship itself into its own documentation.

## Scope

- **Client-only.** No server component, no packets, no faction permissions. `mod.json`
  already declares `client_mod: true` / `server_mod: false`.
- **Annotations we author are local data.** They live on the annotating player's machine
  and are keyed to entities they can see.
- **Annotations baked into an entity are readable.** Since the upstream sync fix
  (see [UPSTREAM-CUSTOM-DATA-SYNC.md](UPSTREAM-CUSTOM-DATA-SYNC.md)), a client can read the
  game's per-block JSON metadata. We cannot *write* it from a client-only mod, but we can
  render what is already there — so annotations shipped inside a blueprint display without
  any file exchange.
- **Export/import is a first-class feature, not an afterthought.** It is the primary
  sharing mechanism for annotations we author, so the file format is part of the core
  design rather than a late add-on.

Deferred until a possible server-side phase: live multiplayer sync, faction-scoped
visibility, server-authoritative storage, and **writing** annotations into per-block JSON
metadata (see Architecture §4 — reading is available now, writing is not).

---

## Architecture

### 1. The annotation model

Everything is a variation on one primitive: an **anchored, persistent drawable owned by
an entity**.

```
Annotation
  id            UUID
  type          LABEL | LEADER_LABEL | REGION | DIMENSION | PLANE | AXIS | MARKER
  anchor        Anchor (see below)
  text          String (optional)
  color         RGBA
  layer         String (layer/group name)
  visibility    ALWAYS | BUILD_MODE_ONLY | HIDDEN
  maxDistance   float (fade/cull range)
  props         type-specific payload
```

Keeping one flat type with a discriminator (rather than a deep class hierarchy) makes
serialization straightforward and keeps the renderer a single switch.

### 2. Anchoring

This is the design decision everything else depends on. An annotation must survive the
entity moving, rotating, docking, and being rebuilt from a blueprint.

**Anchor in core-relative block space, never world space.** Store the block coordinate
relative to the entity core, then transform to world space each frame via the entity's
client world transform.

```
Anchor
  entityKey     String   — SegmentController.getUniqueIdentifier()
  corePos       Vector3f — core-relative block coordinates (abs - SEG_HALF)
  blockIndex    long     — SegmentPiece.getAbsoluteIndex(), optional
  faceNormal    byte     — optional, for surface-mounted labels
  offset        Vector3f — leader-line offset from anchor
```

`blockIndex` is a *hint* for "is my anchor block still there", not the primary key. The
authoritative anchor is `corePos`, so an annotation pointing at empty space (a clearance
volume, a centerline) is just as valid as one pointing at a block.

**Why core-relative and not raw absolute block coordinates.** StarMade stores block
positions in an absolute integer grid whose origin sits at `(SEG_HALF, SEG_HALF,
SEG_HALF)` — the core. But `SEG_HALF` is *not a fixed constant across installs*:

```java
// org/schema/game/common/data/world/Segment.java:30
public static final int DIM_BITS = ByteUtil.Chunk32 ? 5 : 4;
public static final byte DIM      = 1 << DIM_BITS;   // 32 or 16
public static final byte HALF_DIM = DIM / 2;         // 16 or 8
```

Raw absolute coordinates would therefore mean different things on a `Chunk32` install
versus a non-`Chunk32` one, which would silently corrupt imported annotation files.
Subtracting `SegmentData.SEG_HALF` yields a core-relative coordinate that is invariant to
the flag. Store that, and never hardcode `16`.

**Local → world** is exactly what the game already does on the client branch of
`getAbsoluteElementWorldPositionShifted` (`SegmentController.java:1067`):

```java
out.set(x - SegmentData.SEG_HALF, y - SegmentData.SEG_HALF, z - SegmentData.SEG_HALF);
getWorldTransformOnClient().basis.transform(out);
out.add(getWorldTransformOnClient().origin);
```

Since we already store the subtraction baked in, our renderer skips step one and applies
the basis + origin directly.

**Orphan policy** when the anchor block is removed: keep the annotation, mark it
`orphaned`, render it dimmed. Do not silently delete user-authored data. A "clean up
orphaned annotations" action in the list GUI handles the rest.

### 3. Rendering

Register a `ModWorldDrawer` via `RegisterWorldDrawersEvent`. Draw geometry in
`postWorldDraw()`, text last so it composites over the lines.

Two rendering paths:

- **Geometry** (lines, boxes, planes, ticks, arrows) — immediate-mode GL, following the
  pattern in `DebugLine.drawRaw()`. Write our own drawer rather than pushing into
  `DebugDrawer`'s static vectors, which are partly gated behind
  `EngineSettings.P_PHYSICS_DEBUG_ACTIVE`.
- **Text** — reuse `HudIndicatorOverlay.drawString(Indication, Camera, fromPlayer,
  maxDist, WorldToScreenConverter)`. It already handles world→screen projection, distance
  culling, scale, and color. Wrap each label in a `ConstantIndication` whose `Transform`
  we recompute per frame from the anchor.

Per-frame budget matters: a heavily annotated ship could carry hundreds of annotations.
Cull by distance and by active layer *before* building transforms.

### 4. Block custom data — readable now, not writable

The game has a **per-block JSON metadata system**, which is almost exactly the storage this
mod wants:

```java
// SegmentController.java:2480
public void setBlockCustomData(long absIndex, JSONObject data)
public JSONObject getBlockCustomData(long absIndex)
public void removeBlockCustomData(long absIndex)

// SegmentPiece.java:73 — convenience wrappers
public JSONObject getCustomData()
public void setCustomData(JSONObject data)
```

What it gives us for free:

- **Deduplication.** Identical JSON is pooled by canonical string, so a thousand blocks
  tagged `{"layer":"power"}` cost one object.
- **Automatic orphan cleanup.** `removeBlockCustomData` is called when a block is removed
  (`SegmentController.java:1899`) — no dangling-anchor problem at all.
- **Survives block copy/paste.** `BlockProcessor.java:1060` carries custom data onto newly
  placed blocks from a build slot.
- **Travels with blueprints.** `Ship.java:1107-1108` copies the index and pool on ship
  copy, and the tag is written into `ManagerContainer.toTagStructure()`
  (`ManagerContainer.java:1908`), which is what `BlueprintEntry` serializes.
- **Persists with the entity.** Serialized as sub-tag 42 of the `.ent` structure
  (`SegmentController.java:3090`, read back at `:2875`).

There is also an entity-level key/value store alongside it — `getCustomDisplayVariable` /
`setCustomDisplayVariable`, capped at 128 variables, 32-char names, 256-char values —
which is a natural home for entity-wide annotation metadata like the layer list.

**Reading is available now.** Originally this data never reached multiplayer clients at
all, which ruled it out entirely. That turned out to be an upstream defect, now fixed —
see [UPSTREAM-CUSTOM-DATA-SYNC.md](UPSTREAM-CUSTOM-DATA-SYNC.md). A client can ask for the
index of blocks carrying data and then fetch payloads:

```java
provider.requestBlockCustomDataIndex();       // once, per entity
segmentController.hasBlockCustomData(absIndex);
provider.requestBlockCustomData(absIndex);    // fetch one payload
segmentController.getBlockCustomData(absIndex);
```

**Writing is still not available to a client-only mod.** There is no client-to-server API
for setting arbitrary block custom data. The only write path is placing a block whose
*inventory slot* already carries data (`SegmentBuildController.java:716` stages it,
`BlockProcessor.java:1060` commits it server-side), and slot data itself is set by admin
commands, not by mod code. Writing needs a server component.

**Decision:** the two roles split.

- **Authoring → `PersistentObjectUtil`.** Annotations we create are local, exactly as
  planned. Nothing changes here.
- **Reading → block custom data, as an import source.** Annotations already baked into an
  entity render without any file exchange. This is a genuinely better sharing story than
  JSON files for the consume case: download a blueprint, see its annotations.

The migration to writing stays mechanical when the server component lands, because the
anchor key we chose — `SegmentPiece.getAbsoluteIndex()` — is precisely the `absIndex` key
this API uses. That field earns its place in the `Anchor` model now rather than later.

### 5. Persistence

`PersistentObjectUtil` (`addObject` / `getObjects` / `save`, Gson-backed, per-mod) for the
live store. Annotations are grouped by `entityKey` in memory so lookup during the draw
loop is a single map hit rather than a scan.

Save on: annotation create/edit/delete, build-mode exit, and game shutdown.

### 6. Export / import

The sharing mechanism, and therefore load-bearing.

**Format:** JSON, versioned, human-readable and hand-editable.

```jsonc
{
  "format": "betterbuilding.annotations",
  "formatVersion": 1,
  "sourceEntity": "Nomad_Mk_IV",     // informational only
  "sourceBounds": [x, y, z, x, y, z], // for validation on import
  "layers": ["power", "structural", "todo"],
  "annotations": [ /* ... */ ]
}
```

**Portability requirement:** annotations must apply to a *copy* of the ship, not just the
original instance. That means:

- No world coordinates anywhere in the file.
- No dependence on the entity's UID — `sourceEntity` is a label for humans, and import
  re-targets every annotation to whatever entity the player is currently in.
- **Core-relative** block coordinates are the contract, never raw absolute ones, so a file
  exported on a `Chunk32` install imports correctly on one that isn't (see Anchoring). If
  two ships share a blueprint, the same annotation file lands correctly on both.

**Operations:**

- Export current entity's annotations → `mods/BetterBuilding/annotations/<name>.json`
- Import a file onto the current entity, with a merge mode: **replace**, **merge**
  (skip id collisions), or **append** (regenerate ids).
- Export/import a single layer, so a "power routing" annotation set can be shared
  independently.
- Copy/paste to system clipboard for quick sharing in Discord or a forum post.
- On import, validate `sourceBounds` against the target entity's bounds and warn (do not
  block) on mismatch — the file may be for a different ship.

Blueprint-adjacent storage (dropping the JSON next to the `.sment`) is attractive but
depends on how blueprint export is hooked; treat it as a later enhancement.

### 7. Reading annotations out of an entity

A second import source, requiring no file exchange. If an entity carries annotation data
in its per-block JSON — put there by server-side tooling, or by a future server-side phase
of this mod — the client renders it directly.

Shape: reserve a namespaced key so we never collide with anything else using the same
block's custom data.

```jsonc
{
  "bb:annotation": {
    "type": "LEADER_LABEL",
    "text": "Main reactor",
    "layer": "power",
    "color": [1, 0.8, 0.2, 1],
    "offset": [0, 4, 0]
  }
}
```

Read-only, and merged into the render pass as a distinct source so the UI can show which
annotations are baked into the ship versus authored locally — the user cannot edit the
former from a client-only build, and the list GUI should say so rather than silently
failing to save.

Fetch policy: request the index once per entity on
`SegmentControllerFullyLoadedEvent`, then fetch payloads lazily for blocks within render
range. Do not fetch the whole set eagerly; the index exists precisely so we do not have to.

---

## Feature roadmap

### Phase 1 — Core (MVP)

The vertical slice that exercises the whole stack.

- [ ] `Annotation` / `Anchor` model + per-entity in-memory store
- [ ] `ModWorldDrawer` registration and draw loop
- [ ] **Point label** — text pinned to a block coordinate
- [ ] **Leader-line label** — text offset from anchor with a line back to it
- [ ] **Distance dimension** — two anchors, line with end ticks, live length readout
- [ ] Creation flow: build-mode keybind + text-entry dialog
- [ ] Persistence via `PersistentObjectUtil`
- [ ] **Export / import** JSON, with replace/merge/append modes
- [ ] **Read baked-in annotations** from block custom data (`bb:annotation` key),
      rendered read-only alongside locally authored ones

### Phase 2 — Organization

Necessary the moment a real ship accumulates more than a dozen annotations.

- [x] **Annotation list** — every annotation on the current entity, listed individually
      with per-row actions: edit text, cycle size, hide/show, locate, delete. Sortable and
      searchable. "Remove Last" is gone.
- [ ] Per-row colour picker (size and visibility are done; colour is still creation-time only)
- [ ] **Camera jump** from a list row. Currently "Locate" flashes the annotation white for
      a few seconds instead, which answers "which one is this?" without fighting the build
      mode camera. A real camera move needs a way to reposition the build mode view that
      has not been found yet.
- [ ] **Layers/groups** with per-layer visibility toggles, collapsible in the list so a
      ship with hundreds of annotations stays navigable
- [ ] Visibility rules: build-mode-only vs. always, distance fade
- [ ] Orphan detection and cleanup action
- [ ] Per-layer export/import

### Phase 3 — Regions & measurement

- [ ] **Region label** — text anchored to a box volume, floating at its centroid
- [ ] **Named zones** — wireframe box with name and color, optional low-alpha fill
- [ ] **Bounding-box dimension** — 3-axis width × height × depth callout on a selection
- [ ] **Clearance/keepout volumes** — warn when blocks are placed inside
- [ ] Hook `BuildSelection.selectionBoxA/B` so the existing selection can seed a region
- [ ] **Axis rulers / gridlines** — tick marks along an entity axis
- [ ] **Reference planes and axes** — persistent user-placed guides beyond the built-in
      symmetry plane

### Phase 4 — Workflow

- [ ] **Checklists** — annotations with done/not-done state, turning the ship into a build TODO
- [ ] **Face labels** — anchored to a block face + normal, billboarding off the hull surface
- [ ] **Presentation mode** — render annotations with HUD suppressed, for screenshots
- [ ] Clipboard copy/paste of annotation sets

### Phase 5 — Stretch

Larger efforts, each justifying its own design pass.

- [ ] **Section planes** — hide or ghost blocks in front of a plane to expose interior
      structure. Significantly harder than the rest; touches the segment render path.
- [ ] **Auto-annotations** — derived rather than authored: label reactor chambers, mark
      unpowered systems, flag disconnected blocks.
- [ ] Blueprint-adjacent annotation storage
- [ ] **Write annotations into block custom data** (requires a server component) —
      `setBlockCustomData` keyed by `getAbsoluteIndex()`, giving dedup, automatic orphan
      cleanup, copy/paste propagation, and blueprint travel for free. Reading already works
      in Phase 1; only the write path needs the server. See Architecture §4.

---

## Class reference

| Need | Class |
|---|---|
| Register a custom world renderer | `api.utils.draw.ModWorldDrawer` + `api.listener.events.draw.RegisterWorldDrawersEvent` |
| Text at a world transform | `HudIndicatorOverlay.drawString(...)` — `org/schema/game/client/view/gui/shiphud/HudIndicatorOverlay.java:1102` |
| Non-expiring label object | `org.schema.game.client.view.effects.ConstantIndication` (`Transform` + text; `setColor` wraps text in `ColoredInterface`) |
| Line / box / arrow geometry | `org.schema.schine.graphicsengine.forms.debug.DebugLine` (immediate-mode GL pattern), `DebugDrawer` |
| Existing build selection | `BuildSelection.selectionBoxA` / `selectionBoxB` — `org/schema/game/client/controller/manager/ingame/BuildSelection.java:35` |
| Reference for build-mode overlays | `org.schema.game.client.view.BuildModeDrawer` |
| Entity identity | `SegmentController.getUniqueIdentifier()` — `SegmentController.java:1486` |
| Block identity | `SegmentPiece.getAbsoluteIndex()` — `SegmentPiece.java:172` |
| Local → world (client) | `SegmentController.getAbsoluteElementWorldPositionShifted(...)` — `SegmentController.java:1067` |
| Client world transform | `SimpleTransformableSendableObject.getWorldTransformOnClient()` — `:574` |
| Block grid constants | `SegmentData.SEG_HALF` / `Segment.HALF_DIM` — **runtime-variable, never hardcode** |
| Persistence | `api.mod.config.PersistentObjectUtil` |
| Per-block JSON — read | `SegmentController.getBlockCustomData(absIndex)`, `hasBlockCustomData(absIndex)` |
| Per-block JSON — fetch | `SendableSegmentProvider.requestBlockCustomDataIndex()`, `requestBlockCustomData(absIndex)` |
| Per-block JSON — write | `setBlockCustomData` — **server-side only**, needs a server component |
| Entity key/value | `SegmentController.getCustomDisplayVariable` (read, now synced) / `setCustomDisplayVariable` (server-side) |
| Text input dialog | `api.utils.gui.SimplePlayerTextInput`, `GUIInputDialog`, `SimplePopup`, `ModGUIHandler` |
| Entity ready signal | `api.listener.events.entity.SegmentControllerFullyLoadedEvent` |
| Input hooks | `api.listener.events.input.KeyPressEvent`, `MousePressEvent` |
| GUI hooks | `AdvancedBuildModeGUICreateEvent`, `PlayerGUIDrawEvent`, `ChatMessageParseEvent` |

## File layout

```
videogoose/betterbuilding/
  BetterBuilding.java          — mod entry, event registration
  annotation/
    Annotation.java            — model
    Anchor.java                — entity-local anchoring
    AnnotationType.java
    AnnotationStore.java       — per-entity store, persistence
    Layer.java
  render/
    AnnotationDrawer.java      — ModWorldDrawer implementation
    GeometryRenderer.java      — lines, boxes, ticks, planes
    LabelRenderer.java         — Indication-based text
  io/
    AnnotationExporter.java
    AnnotationImporter.java
    AnnotationFormat.java      — versioned JSON schema
  ui/
    AnnotationListPanel.java
    AnnotationEditDialog.java
  input/
    AnnotationControls.java    — keybinds, build-mode integration
```

## Resolved design questions

### 1. `getUniqueIdentifier()` is stable — use it as the entity key ✅

- UID is assigned once at spawn (`EntityRequest.getNewShip`, etc.) in the form
  `ENTITY_SHIP_<name>_<timestamp>`, and is never regenerated afterward.
- It is **persisted**: serialized into the entity tag as `uniqueId`
  (`SegmentController.java:2987` / `:2714`) and used as the entity database *filename*
  (`ENTITY_DATABASE_PATH + getUniqueIdentifier() + ".ent"`, `:885`). It has to be stable
  across sessions or the game could not reload its own entities.
- **Renaming does not change it.** `realName` is a separate field with its own setter
  (`:3478`); `setRealName` never touches `uniqueIdentifier`. The name baked into the UID
  string is a historical artifact of the creation-time name, not a live value.
- **Docking does not change it.** No `setUniqueIdentifier` call exists anywhere in the
  docking path.
- **It reaches the client.** `NetworkSegmentController.uniqueIdentifier` is a
  `RemoteString`, applied client-side at `SendableSegmentController.java:908`.

*Caveat:* UID is only meaningful once the entity has synced. Gate annotation binding on
`SegmentControllerFullyLoadedEvent` rather than reading it opportunistically.

### 2. Block coordinates are stable as the ship grows ✅

Absolute block coordinates live in a fixed grid anchored at the core; they are **not**
relative to the current bounding box. `getMinPos()` / `getMaxPos()` are in *segment*
units and serve only as a bounding box (`SegmentController.java:578-584`) — they expand as
the ship grows, but existing blocks are never renumbered.

So a ship growing in the negative direction does not shift any previously placed block,
and annotations will not drift. Anchoring is safe.

The one trap is `SEG_HALF` not being a portable constant — handled in Anchoring above.

### 3. Anchor to the docked entity itself, not the parent ✅

Docked entities (turrets included) are **separate `SegmentController`s with their own
UIDs** — confirmed by `SegmentController.java:3225`, which logs a docked entity's UID
independently of its parent's.

No manual docking-chain resolution is needed: `getWorldTransformOnClient()` returns
`remoteTransformable.getWorldTransform()` (`SimpleTransformableSendableObject.java:574`),
the entity's *actual* physics world transform, which already accounts for being carried by
a parent. Anchoring to the turret's own UID and core-relative coordinates therefore gives
the correct result for free, and it is also the semantically right choice — a turret
annotation should follow the turret when it is undocked and reused.

*Caveat:* that method returns a stale `clientTransform` when the entity is in a different
sector than the player. Skip entities failing
`getSectorId() == state.getCurrentSectorId()`.

### 4. Use StarLoader's text input ✅

`api.utils.gui.SimplePlayerTextInput` is an abstract wrapper over `PlayerGameTextInput` —
subclass it, implement `onInput(String)`, done. It self-activates in the constructor. The
64-character limit is hardcoded in the wrapper, so multi-line or longer notes will need
`PlayerGameTextInput` directly or `GUITextAreaInputPanel`.

Related helpers already available: `GUIInputDialog`, `SimplePopup`, `ModGUIHandler`,
`SimpleGUIVerticalButtonPane`. No chat-command fallback needed.

## Remaining open questions

### A. Client sync of custom data — resolved and fixed upstream

**Closed.** The sync gap was a defect in the game, not a constraint to design around, and
is fixed on StarMade's `fix/json-data-fixes` branch. See
[UPSTREAM-CUSTOM-DATA-SYNC.md](UPSTREAM-CUSTOM-DATA-SYNC.md).

Consequence for this plan: reading block custom data is now a Phase 1 capability
(Architecture §7), writing it still needs a server component. The storage decision for
annotations we author is unchanged — `PersistentObjectUtil`.

Caveat: the upstream fix compiles but has not been runtime-tested yet. If the read path
turns out not to work as expected, Phase 1's baked-in-annotation import is the only item
that depends on it; everything else is unaffected.

### B. Draw-loop cost

Not answerable from source — it needs measurement. Per annotation per
frame the work is a basis transform plus a world→screen projection plus a text draw, which
should be cheap in the tens-to-low-hundreds range. The text path goes through
`GUITextOverlay`, which is the likely bottleneck well before the geometry is.

Plan: build Phase 1 with the straightforward flat iteration, then profile with a few
hundred annotations on one entity. Distance culling and layer filtering must happen
*before* transforms are built, which is the cheap mitigation. Only add spatial
partitioning if profiling shows it is needed.
