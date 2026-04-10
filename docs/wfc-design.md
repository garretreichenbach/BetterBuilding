# Wave Function Collapse Generator — Design

## Motivation

We tried fine-tuning a small LLM (Gemma 4 E2B) to emit `fill` / `shell` /
`line` / `place` tool calls that reproduce StarMade templates. It does not
work: the corpus has ~174 hand-built templates, individual ships decompose
into hundreds-to-thousands of `place` calls, and LLMs have no spatial
inductive bias — they tokenize voxels wastefully, can't see the partial grid
they're building, and burn context on coordinate arithmetic they're bad at.

WFC is the right tool for this job. It is purpose-built for "given example
chunks, generate new chunks with the same local statistics," runs in
milliseconds, has zero training, and integrates cleanly with the existing
template I/O path.

LLMs come back later as a *planner* (Phase 3), not as a voxel emitter.

## Algorithm

**Block-level overlapping WFC**, starting with 1×1×1 windows.

- **Tile** = `(blockType, orientation)` pair extracted from the example
  templates. Air (type 0) is a first-class tile so the model can place empty
  space inside the volume.
- **Adjacency rules** are learned from template-internal neighbors. For each
  axis (±X, ±Y, ±Z) we record which tile pairs are observed adjacent in the
  example corpus.
- **Frequency table**: how often each tile appears across the corpus. Used
  to bias entropy and weighted choice during collapse.
- **Out-of-bounds** = air. Matches the natural "templates float in empty
  space" assumption and avoids needing wraparound.

If 1×1×1 produces mushy results, the same code path upgrades to **2×2×2
overlapping patterns** in Phase 2 — captures local structure ("armor edge
meets glass") instead of just "armor touches glass somewhere."

## Solver

- **Min-entropy heuristic**: collapse the cell with fewest remaining options
  first (Shannon entropy weighted by tile frequency).
- **AC-3 style propagation**: when a cell collapses, prune neighbors' option
  sets and recurse.
- **Restart on contradiction**, with a configurable max-restart count. Full
  backtracking is overkill for v1; restart is simpler and works fine for
  most WFC use cases.
- **Seedable RNG** so generations are reproducible.

## Open design decisions (resolved)

- **Air-aware adjacency**: Air adjacency is *learned*, not unconstrained.
  More faithful to the user's building style; restart-on-contradiction
  absorbs the extra failure rate.
- **Orientation as part of tile identity**: Yes. A rotated wedge is a
  distinct tile from its un-rotated twin. Orientation is load-bearing in
  StarMade. The palette is bigger but still tractable for 174 templates.

## Code layout

New package `videogoose.betterbuilding.data.wfc`:

| File | Responsibility |
|---|---|
| `WfcTile.java` | `(short type, byte orientation)` value object + interning |
| `WfcRuleset.java` | Built once from a `List<TemplateMetaData>`. Holds tile palette, frequency counts, and per-axis adjacency sets. |
| `WfcGenerator.java` | Takes a ruleset + target `(sizeX, sizeY, sizeZ)` + seed, returns a `TemplateMetaData`. |
| `WfcCell.java` | Internal: bitset of allowed tile indices, cached entropy. |

Reuses the existing `TemplateMetaData` for I/O so generated results plug
straight into the rest of the mod (preview, paste, save).

## Integration point

New chat command:

```
/bb_wfc <sizeX> <sizeY> <sizeZ> [-seed N] [-templates pattern]
```

- Loads matching templates from `./templates/` via the same path
  `ExportTrainingDataCommand` uses.
- Builds the ruleset.
- Runs WFC.
- Saves the result as a new template named `wfc_<timestamp>.smtpl` so it can
  be pasted in-world like any other template.

Deliberately does **not** touch `GenerateTemplateCommand` (the LLM path) so
the two approaches stay independent and comparable.

## Phases

1. **Phase 1 (done)** — Block-level WFC + `/bb_wfc` command. Result was
   exactly the predicted "mushy" output: locally-correct micro-statistics
   (armor cubes touch armor cubes), zero global structure, lots of floating
   debris. Confirmed the limit of 1×1×1 WFC. Implementation lives in
   `WfcRuleset` + `WfcGenerator` and is still selectable via `-mode block`
   for comparison.
2. **Phase 2 (done)** — 2×2×2 overlapping patterns in
   `WfcPatternRuleset` + `WfcOverlappingGenerator`. Default mode of
   `/bb_wfc`. Templates are extracted with N-1 air padding on every side so
   the corpus learns its own boundary transitions. Compatibility is built
   via slab signatures (4 tile indices packed into a long for N=2) and an
   inverted index, which keeps ruleset construction O(P) instead of O(P²).
3. **Phase 3** — Off-the-shelf LLM as a planner. Natural language → high-
   level constraints (region masks, "this region is a corridor," "this face
   is the hull") → WFC respects them as hard constraints during collapse.

## Out of scope for Phase 1

- Multi-template style mixing controls
- Symmetry constraints (mirror, rotational)
- Hard-placed seed blocks before collapse
- Backtracking beyond restart
- Anything LLM-related

## Dead code to remove once WFC is confirmed working

- `scripts/train/train_unsloth.py`
- `scripts/train/prepare_dataset.py`
- `scripts/train/inspect_longest.py`
- `scripts/train/README.md`
- `TrainingDataExporter.java`
- `ExportTrainingDataCommand.java`

The bug fix in `TemplateMetaData.fromRawTemplate` (defensive bounds from
pieces) stays — it's a real fix to template loading that benefits any
consumer of `CopyArea`, including WFC.
