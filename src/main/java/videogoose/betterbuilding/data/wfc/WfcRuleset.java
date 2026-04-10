package videogoose.betterbuilding.data.wfc;

import videogoose.betterbuilding.data.template.TemplateMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The learned WFC model: a tile palette, frequency counts, and per-axis
 * adjacency rules extracted from a set of example templates.
 *
 * Built once per generation. Templates contribute neighbors only within their
 * own bounds — pieces from different templates are never considered adjacent.
 *
 * Out-of-bounds is treated as air, both during learning (so corners and faces
 * of source templates teach "this tile may sit next to air") and during
 * solving. This matches the assumption that templates float in empty space.
 */
public final class WfcRuleset {

	/** Six face axes, indexed 0..5. */
	public static final int AXIS_PX = 0;
	public static final int AXIS_NX = 1;
	public static final int AXIS_PY = 2;
	public static final int AXIS_NY = 3;
	public static final int AXIS_PZ = 4;
	public static final int AXIS_NZ = 5;
	public static final int AXIS_COUNT = 6;

	private static final int[] DX = {+1, -1, 0, 0, 0, 0};
	private static final int[] DY = {0, 0, +1, -1, 0, 0};
	private static final int[] DZ = {0, 0, 0, 0, +1, -1};

	private final List<WfcTile> palette;            // index -> tile
	private final Map<WfcTile, Integer> tileIndex;  // tile  -> index
	private final int[] frequencies;                // index -> count

	/**
	 * allowed[axis][fromTileIdx] is a bitset (long[]) over toTileIdx telling
	 * us which tiles may legally sit on the +axis side of fromTile.
	 */
	private final long[][][] allowed;

	private WfcRuleset(List<WfcTile> palette,
	                   Map<WfcTile, Integer> tileIndex,
	                   int[] frequencies,
	                   long[][][] allowed) {
		this.palette = palette;
		this.tileIndex = tileIndex;
		this.frequencies = frequencies;
		this.allowed = allowed;
	}

	public int paletteSize() {
		return palette.size();
	}

	public WfcTile tile(int idx) {
		return palette.get(idx);
	}

	public int indexOf(WfcTile t) {
		Integer i = tileIndex.get(t);
		return i == null ? -1 : i;
	}

	public int frequency(int idx) {
		return frequencies[idx];
	}

	/** Number of long words needed to store one bitset over the palette. */
	public int wordsPerBitset() {
		return (palette.size() + 63) >>> 6;
	}

	/**
	 * Returns the bitset of tiles allowed on the +axis side of fromTile.
	 * The returned array is shared — do not mutate.
	 */
	public long[] allowedNeighbors(int axis, int fromTileIdx) {
		return allowed[axis][fromTileIdx];
	}

	public static int oppositeAxis(int axis) {
		return axis ^ 1; // PX<->NX, PY<->NY, PZ<->NZ
	}

	public static int dx(int axis) { return DX[axis]; }
	public static int dy(int axis) { return DY[axis]; }
	public static int dz(int axis) { return DZ[axis]; }

	// ------------------------------------------------------------------
	// Build
	// ------------------------------------------------------------------

	/**
	 * Learn a ruleset from a set of templates. Air (type 0) is always present
	 * in the palette, even if no template contains an interior air block,
	 * because boundary cells are treated as air.
	 */
	public static WfcRuleset learn(Collection<TemplateMetaData> templates) {
		if(templates == null || templates.isEmpty()) {
			throw new IllegalArgumentException("WFC ruleset needs at least one example template");
		}

		// --- Pass 1: build palette + frequency counts ---
		Map<WfcTile, Integer> idx = new HashMap<>();
		List<WfcTile> pal = new ArrayList<>();
		List<Integer> freq = new ArrayList<>();

		// Air is always tile index 0.
		idx.put(WfcTile.AIR, 0);
		pal.add(WfcTile.AIR);
		freq.add(0);

		for(TemplateMetaData t : templates) {
			int[] dims = t.getDimensions();
			short[] types = t.getBlockTypes();
			byte[] orients = t.getBlockOrientations();
			int total = dims[0] * dims[1] * dims[2];
			for(int i = 0; i < total; i++) {
				WfcTile tile = (types[i] == 0) ? WfcTile.AIR : new WfcTile(types[i], orients[i]);
				Integer existing = idx.get(tile);
				if(existing == null) {
					existing = pal.size();
					idx.put(tile, existing);
					pal.add(tile);
					freq.add(0);
				}
				freq.set(existing, freq.get(existing) + 1);
			}
		}

		int paletteSize = pal.size();
		int[] frequencies = new int[paletteSize];
		for(int i = 0; i < paletteSize; i++) frequencies[i] = freq.get(i);

		// --- Pass 2: collect adjacency observations ---
		// adjacency[axis] is a bitset[paletteSize][words] where bit j of row i
		// is set if we observed tile j on the +axis side of tile i.
		int words = (paletteSize + 63) >>> 6;
		long[][][] allowed = new long[AXIS_COUNT][paletteSize][words];

		for(TemplateMetaData t : templates) {
			int[] dims = t.getDimensions();
			int sx = dims[0], sy = dims[1], sz = dims[2];
			short[] types = t.getBlockTypes();
			byte[] orients = t.getBlockOrientations();

			for(int z = 0; z < sz; z++) {
				for(int y = 0; y < sy; y++) {
					for(int x = 0; x < sx; x++) {
						int here = x + y * sx + z * sx * sy;
						short ht = types[here];
						WfcTile hereTile = (ht == 0) ? WfcTile.AIR : new WfcTile(ht, orients[here]);
						int hereIdx = idx.get(hereTile);

						for(int axis = 0; axis < AXIS_COUNT; axis++) {
							int nx = x + DX[axis];
							int ny = y + DY[axis];
							int nz = z + DZ[axis];
							int neighborIdx;
							if(nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
								neighborIdx = 0; // out of bounds = air
							} else {
								int ni = nx + ny * sx + nz * sx * sy;
								short nt = types[ni];
								WfcTile neighbor = (nt == 0) ? WfcTile.AIR : new WfcTile(nt, orients[ni]);
								neighborIdx = idx.get(neighbor);
							}
							setBit(allowed[axis][hereIdx], neighborIdx);
						}
					}
				}
			}
		}

		return new WfcRuleset(pal, idx, frequencies, allowed);
	}

	private static void setBit(long[] bits, int i) {
		bits[i >>> 6] |= 1L << (i & 63);
	}

	// ------------------------------------------------------------------

	@Override
	public String toString() {
		long edges = 0;
		for(int axis = 0; axis < AXIS_COUNT; axis++) {
			for(int i = 0; i < palette.size(); i++) {
				for(long w : allowed[axis][i]) edges += Long.bitCount(w);
			}
		}
		return "WfcRuleset(tiles=" + palette.size()
				+ ", adjacencyEdges=" + edges
				+ ", totalBlocks=" + Arrays.stream(frequencies).sum() + ")";
	}
}
