# Upstream: custom data never reaches multiplayer clients

Analysis of a defect in StarMade itself (`/Users/garret/Documents/GitHub/StarMade`),
found while scoping BetterBuilding's annotation storage. Two related gaps, one clearly a
bug, one an unfinished feature half.

## Status: fixed on `fix/json-data-fixes`

| Commit | Change |
|---|---|
| `9629139` | Bug A - sync custom display variables to clients |
| `7d36cde` | Reference-count the block custom data pool (O(1) release, fixes overwrite leak) |
| `a6872a4` | Bug B - sync block custom data to clients (eager index, lazy payload) |

Compiles clean. **Not yet runtime-tested** - see "Still to verify" at the end.

The storage-level pool-id refactor described below was deliberately **not** done; see
"What changed vs. the original proposal".

---

## Bug A — `customDisplayVariables` is never synced

**This one is unambiguous, and the code comments state the intent.**

The display-variable feature is split across server and client by design:

- **Server writes.** `SendableSegmentController.DisplayReplace` parses `[set:name=value]`
  and `[unset:name]` out of text box content and mutates the entity's
  `customDisplayVariables` map (`SendableSegmentController.java:2429`, with the comment
  *"Process [set:varName=value] and [unset:varName] tags server-side"*).
- **Client reads.** `AbstractTextBox` substitutes `[var:name]` from
  `getCustomDisplayVariable(...)` when rendering (`AbstractTextBox.java:651`), commented
  *"Variables are only mutated server-side (in DisplayReplace); client just hides the
  tag"*.

The split is deliberate. The problem is that **nothing carries the map from server to
client.** `customDisplayVariables` appears only in:

- `SegmentController.java:172` — the field
- `getCustomDisplayVariablesTag()` / `readCustomDisplayVariablesTag()` — tag I/O
- `ManagerContainer.java:1908` / `:620` — inside the container tag
- `SegmentController.java:3091` / `:2880` — sub-tag 43 of the `.ent` structure

Every one of those paths is entity-database persistence or blueprint serialization. There
is no `Remote*` field on `NetworkSegmentController` and no packet.

**Effect:** `[var:name]` resolves to `""` on every multiplayer client, because the client's
map is permanently empty. In singleplayer the integrated server shares the JVM, so the
same map is read directly and the feature appears to work. Classic works-in-SP,
silently-broken-in-MP.

## Bug B — `blockCustomData` has no client-visible path

Less clearly a bug, more an unfinished half. Nothing in the game reads block custom data
on the client today, so nothing is visibly broken — but every other part of the feature is
fully wired:

| Path | Status |
|---|---|
| Persist to `.ent` | ✅ sub-tag 42, `SegmentController.java:3090` / `:2875` |
| Travel with blueprints | ✅ `ManagerContainer.toTagStructure()`, `BlueprintEntry.java:1554` |
| Survive ship copy | ✅ `Ship.java:1107-1108` |
| Slot → block on place | ✅ `BlockProcessor.java:1060` |
| Block → slot on remove | ✅ `BlockProcessor.java:1183` |
| Auto-cleanup on block removal | ✅ `SegmentController.java:1899` |
| **Inventory slot custom data over the wire** | ✅ `Inventory.java:133` / `:173` |
| **Block custom data over the wire** | ❌ **missing** |

The inventory half of the exact same data is network-synced. The block half is not, which
is what makes this read as an oversight rather than a deliberate omission — a block's data
is visible to a client while the block sits in inventory, and vanishes the moment it is
placed.

---

## Proposed fix

### Bug A — small, do it directly

`customDisplayVariables` is bounded and tiny by construction:
`MAX_CUSTOM_VARIABLES = 128`, `MAX_VARIABLE_NAME_LENGTH = 32`,
`MAX_VARIABLE_VALUE_LENGTH = 256` (`SegmentController.java:173-175`). Worst case is ~37 KB,
typical case a handful of entries.

Add a full-state remote field on `NetworkSegmentController`, updated when the map changes:

- New `RemoteDisplayVariableBuffer` (or reuse a generic string-pair buffer), mirroring the
  `RemoteTextBlockBuffer` / `RemoteTextBlockPair` pair structure.
- Server: mark dirty in `setCustomDisplayVariable` / `removeCustomDisplayVariable`, flush
  in the existing update loop.
- Client: apply in `SendableSegmentController.updateFromNetworkObject`, alongside where
  `textBlockChangeBuffer` is drained (`:964`).

Deltas rather than full state would also work, but at this size full state on change is
simpler and self-healing.

### Bug B — needs a design decision first

Block custom data is unbounded, so the sync strategy matters. Three options:

**1. Full index + pool on entity load.** Simplest. Risk: unbounded payload on a heavily
annotated entity.

**2. Request/response + change buffer, mirroring text blocks.** Most consistent with
existing patterns (`textBlockRequests` → `textBlockResponsesAndChangeRequests` →
`textBlockChangeBuffer`). Problem: text blocks are *discoverable* — the client knows a
block may have text because it can see the block is a text box block. **Any** block can
carry custom data, so a client has no way to know what to request without an index.

**3. Hybrid — eager index, lazy pool.** Sync `absIndex → poolId` on load so the client
knows *where* data exists, and fetch pool entries on demand. Best fit for the annotation
use case, and degrades gracefully.

**Recommendation: option 3.**

### Worth fixing while in there: the pool key

`blockCustomDataIndex` is `Long2ObjectOpenHashMap<String>` where the `String` value is the
**entire canonical JSON document** (`SegmentController.java:2484-2487`):

```java
String canonical = data.toString();
blockCustomDataPool.putIfAbsent(canonical, new JSONObject(canonical));
blockCustomDataIndex.put(absIndex, canonical);
```

The pool deduplicates `JSONObject` *instances*, but the index still holds a full JSON
string reference per block. Ten thousand blocks sharing one payload store ten thousand
references to one string — fine for heap, but it means the wire format would ship the
whole document per block unless we intern it first.

Introducing an `int` pool id would fix the wire format and tidy the in-memory structure:
`Long2IntMap` index, `Int2ObjectMap` pool. This changes the tag format, so it needs a
version guard in `readBlockCustomDataTag` to keep existing saves loadable.

Also worth noting: `removeBlockCustomData` does a linear scan
(`blockCustomDataIndex.values().contains(canonical)`, `:2506`) on **every block removal**.
That is O(blocks-with-custom-data) per removed block — fine at today's usage of roughly
zero, but it becomes a real hot-path cost the moment anything populates it at scale. A
refcount alongside the pool removes it. Worth fixing in the same pass, since annotations
would be the first feature to actually make it hurt.

---

---

## What changed vs. the original proposal

**The storage-level pool-id refactor was dropped.** The motivation was that
`blockCustomDataIndex` maps `long -> String` where the String is the whole canonical JSON
document, so a naive wire format would ship the full document per block. But that concern
lives entirely at the network layer, and the chosen sync strategy sidesteps it: the eager
index carries only `long` block indices with no payloads at all, and payloads are fetched
one at a time. There is nothing to intern.

Doing the refactor anyway would have changed the on-disk tag format (migration risk for
every existing save) and the public `getBlockCustomDataIndex()` / `getBlockCustomDataPool()`
signatures (breaking mods), to buy nothing the sync design does not already get. It stays
available as a future memory optimisation if custom data ever gets used at a scale where
the per-block document reference matters.

**The refcount fix was kept**, because it is independent of the wire format and fixes two
real defects: the O(blocks-with-custom-data) linear scan on every block removal, and a
leak where overwriting a block's data never released the old pool entry.

## Notes for review

**Protocol break.** Network field ids are assigned by *sorted field name*
(`NetworkObject.java:495-505`), not declaration order, so adding any field shifts the ids
of every alphabetically later field. Client and server must be on matching versions.
Declaration placement is irrelevant. Field counts are well under the 254 assertion.

**Nothing calls the new client API yet.** `requestBlockCustomDataIndex()` and
`requestBlockCustomData(long)` are inert until game or mod code calls them, matching how
`requestCurrentControlMap()` works. This keeps the cost at zero for entities nobody is
inspecting.

## Still to verify at runtime

Everything below is reasoned from source and compiles, but has not been exercised in a
running client/server pair:

1. `[var:name]` now resolves on a multiplayer client after a display block sets it.
2. The eager index arrives and `hasBlockCustomData` answers correctly before any payload
   fetch.
3. A payload fetch round-trips, and an in-flight request is not duplicated.
4. A server-side change to a block's custom data reaches an observing client.
5. Existing saves still load - `readBlockCustomDataTag` is unchanged in format and now
   rebuilds derived state, so this should be safe, but it is the highest-blast-radius
   path touched.
