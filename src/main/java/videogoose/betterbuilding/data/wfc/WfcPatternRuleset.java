package videogoose.betterbuilding.data.wfc;

import videogoose.betterbuilding.data.template.TemplateMetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overlapping-pattern WFC ruleset. The unit of analysis is an NxNxN
 * "pattern" (a small block of (type, orientation) tiles) extracted by
 * sliding a window over each example template.
 *
 * Templates are padded with N-1 air voxels on every side before extraction
 * so that patterns near the original boundary capture how the template
 * transitions to empty space — without that, the solver has no idea how to
 * end a hull and tends to leave dangling fragments.
 *
 * Compatibility between two patterns A and B on a given axis means their
 * overlapping (N-1)-slab is identical. We compute that via slab signatures
 * (4 tile indices packed into a long for N=2) and an inverted index, so the
 * O(P^2) naive comparison becomes O(P) build + per-pattern hash lookups.
 *
 * Restrictions:
 *   - patternSize is hard-capped at 2 because the slab signature packs four
 *     16-bit tile indices into one long. Going to N=3 would mean nine tile
 *     indices per slab and a different signature type.
 */
public final class WfcPatternRuleset {

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

	private final int patternSize;                  // N
	private final List<WfcTile> tilePalette;        // tile index -> WfcTile
	private final int[][] patterns;                 // patternIdx -> int[N*N*N] of tile indices
	private final int[] patternFrequencies;
	private final long[][][] allowed;               // [axis][patternIdx] -> bitset over patternIdx

	private WfcPatternRuleset(int patternSize,
	                          List<WfcTile> tilePalette,
	                          int[][] patterns,
	                          int[] patternFrequencies,
	                          long[][][] allowed) {
		this.patternSize = patternSize;
		this.tilePalette = tilePalette;
		this.patterns = patterns;
		this.patternFrequencies = patternFrequencies;
		this.allowed = allowed;
	}

	public int patternSize() { return patternSize; }
	public int patternCount() { return patterns.length; }
	public int wordsPerBitset() { return (patterns.length + 63) >>> 6; }
	public int frequency(int p) { return patternFrequencies[p]; }
	public long[] allowedNeighbors(int axis, int p) { return allowed[axis][p]; }

	public WfcTile tile(int tileIdx) { return tilePalette.get(tileIdx); }
	public int[] pattern(int p) { return patterns[p]; }

	public static int dx(int axis) { return DX[axis]; }
	public static int dy(int axis) { return DY[axis]; }
	public static int dz(int axis) { return DZ[axis]; }

	// ------------------------------------------------------------------

	public static WfcPatternRuleset learn(Collection<TemplateMetaData> templates, int N) {
		return learn(templates, N, 1);
	}

	/**
	 * @param minFrequency drop any pattern observed fewer than this many times
	 *                     across the entire corpus. Use to trim singleton-noise
	 *                     patterns and shrink the bitset / support memory.
	 */
	public static WfcPatternRuleset learn(Collection<TemplateMetaData> templates, int N, int minFrequency) {
		if(templates == null || templates.isEmpty()) {
			throw new IllegalArgumentException("WFC pattern ruleset needs at least one template");
		}
		if(N != 2) {
			throw new IllegalArgumentException("Only N=2 is supported (slab signature packs 4 tile ids into a long)");
		}
		if(minFrequency < 1) minFrequency = 1;

		// --- Tile palette + interning ---
		Map<WfcTile, Integer> tileIdx = new HashMap<>();
		List<WfcTile> tilePalette = new ArrayList<>();
		tileIdx.put(WfcTile.AIR, 0);
		tilePalette.add(WfcTile.AIR);

		// --- Pattern dedup ---
		Map<PatternKey, Integer> patternIdx = new HashMap<>();
		List<int[]> patternList = new ArrayList<>();
		List<Integer> patternFreqList = new ArrayList<>();

		int patternVolume = N * N * N;
		int pad = N - 1;

		// --- Extraction with N-1 air padding on every side ---
		for(TemplateMetaData t : templates) {
			int[] dims = t.getDimensions();
			int sx = dims[0], sy = dims[1], sz = dims[2];
			short[] types = t.getBlockTypes();
			byte[] orients = t.getBlockOrientations();

			int paddedX = sx + 2 * pad;
			int paddedY = sy + 2 * pad;
			int paddedZ = sz + 2 * pad;

			int[] buf = new int[patternVolume];
			for(int cz = 0; cz <= paddedZ - N; cz++) {
				for(int cy = 0; cy <= paddedY - N; cy++) {
					for(int cx = 0; cx <= paddedX - N; cx++) {
						for(int dz = 0; dz < N; dz++) {
							for(int dy = 0; dy < N; dy++) {
								for(int dx = 0; dx < N; dx++) {
									int tx = cx + dx - pad;
									int ty = cy + dy - pad;
									int tz = cz + dz - pad;
									int idx;
									if(tx < 0 || ty < 0 || tz < 0
											|| tx >= sx || ty >= sy || tz >= sz) {
										idx = 0; // air pad
									} else {
										int li = tx + ty * sx + tz * sx * sy;
										short tt = types[li];
										WfcTile tile = (tt == 0)
												? WfcTile.AIR
												: new WfcTile(tt, orients[li]);
										Integer existing = tileIdx.get(tile);
										if(existing == null) {
											existing = tilePalette.size();
											tileIdx.put(tile, existing);
											tilePalette.add(tile);
										}
										idx = existing;
									}
									buf[dx + dy * N + dz * N * N] = idx;
								}
							}
						}
						PatternKey key = new PatternKey(buf.clone());
						Integer pid = patternIdx.get(key);
						if(pid == null) {
							pid = patternList.size();
							patternIdx.put(key, pid);
							patternList.add(key.tiles);
							patternFreqList.add(1);
						} else {
							patternFreqList.set(pid, patternFreqList.get(pid) + 1);
						}
					}
				}
			}
		}

		int patternCount = patternList.size();
		int[][] patterns = patternList.toArray(new int[0][]);
		int[] patternFreqs = new int[patternCount];
		for(int i = 0; i < patternCount; i++) patternFreqs[i] = patternFreqList.get(i);

		// Drop low-frequency patterns. Singletons are often pure noise — they
		// inflate pattern count, blow up the support array, and contribute
		// nothing to diversity since each one only fits in one specific
		// neighborhood from the corpus.
		if(minFrequency > 1) {
			int kept = 0;
			for(int i = 0; i < patternCount; i++) {
				if(patternFreqs[i] >= minFrequency) {
					patterns[kept] = patterns[i];
					patternFreqs[kept] = patternFreqs[i];
					kept++;
				}
			}
			if(kept == 0) {
				throw new IllegalStateException("All patterns dropped at minFrequency=" + minFrequency
						+ ". Lower the threshold or add more example templates.");
			}
			patterns = Arrays.copyOf(patterns, kept);
			patternFreqs = Arrays.copyOf(patternFreqs, kept);
			patternCount = kept;
		}

		if(tilePalette.size() > 0xFFFF) {
			throw new IllegalStateException("Tile palette exceeded 65535 entries — slab signature would overflow");
		}

		// --- Compatibility via inverted slab index ---
		// posSlab[dim][p] = signature of pattern p's plane at the +dim face
		// negSlab[dim][p] = signature of pattern p's plane at the -dim face
		// A is +dim-compatible with B iff A.posSlab[dim] == B.negSlab[dim]
		long[][] posSlab = new long[3][patternCount];
		long[][] negSlab = new long[3][patternCount];
		for(int p = 0; p < patternCount; p++) {
			int[] tiles = patterns[p];
			posSlab[0][p] = computeSlab(tiles, N, 0, N - 1);
			negSlab[0][p] = computeSlab(tiles, N, 0, 0);
			posSlab[1][p] = computeSlab(tiles, N, 1, N - 1);
			negSlab[1][p] = computeSlab(tiles, N, 1, 0);
			posSlab[2][p] = computeSlab(tiles, N, 2, N - 1);
			negSlab[2][p] = computeSlab(tiles, N, 2, 0);
		}

		int words = (patternCount + 63) >>> 6;
		long[][][] allowed = new long[AXIS_COUNT][patternCount][words];

		for(int dim = 0; dim < 3; dim++) {
			Map<Long, IntList> negIndex = new HashMap<>();
			Map<Long, IntList> posIndex = new HashMap<>();
			for(int p = 0; p < patternCount; p++) {
				negIndex.computeIfAbsent(negSlab[dim][p], k -> new IntList()).add(p);
				posIndex.computeIfAbsent(posSlab[dim][p], k -> new IntList()).add(p);
			}
			int axisPos = dim * 2;     // +X=0, +Y=2, +Z=4
			int axisNeg = dim * 2 + 1; // -X=1, -Y=3, -Z=5
			for(int p = 0; p < patternCount; p++) {
				IntList posCompat = negIndex.get(posSlab[dim][p]);
				if(posCompat != null) {
					for(int i = 0; i < posCompat.size; i++) {
						setBit(allowed[axisPos][p], posCompat.data[i]);
					}
				}
				IntList negCompat = posIndex.get(negSlab[dim][p]);
				if(negCompat != null) {
					for(int i = 0; i < negCompat.size; i++) {
						setBit(allowed[axisNeg][p], negCompat.data[i]);
					}
				}
			}
		}

		return new WfcPatternRuleset(N, tilePalette, patterns, patternFreqs, allowed);
	}

	/**
	 * Pack the NxN slab of `tiles` at the given axis (0=X, 1=Y, 2=Z) and plane
	 * coordinate (0 or N-1) into a long. For N=2, this is 4 tile indices * 16
	 * bits = 64 bits, fits exactly. Larger N would overflow and need a
	 * different signature representation.
	 */
	private static long computeSlab(int[] tiles, int N, int axis, int plane) {
		long sig = 0;
		for(int a = 0; a < N; a++) {
			for(int b = 0; b < N; b++) {
				int dx, dy, dz;
				if(axis == 0)      { dx = plane; dy = a; dz = b; }
				else if(axis == 1) { dx = a; dy = plane; dz = b; }
				else               { dx = a; dy = b; dz = plane; }
				int v = tiles[dx + dy * N + dz * N * N];
				sig = (sig << 16) | (v & 0xFFFFL);
			}
		}
		return sig;
	}

	private static void setBit(long[] bits, int i) {
		bits[i >>> 6] |= 1L << (i & 63);
	}

	@Override
	public String toString() {
		long edges = 0;
		for(int axis = 0; axis < AXIS_COUNT; axis++) {
			for(int p = 0; p < patterns.length; p++) {
				for(long w : allowed[axis][p]) edges += Long.bitCount(w);
			}
		}
		return "WfcPatternRuleset(N=" + patternSize
				+ ", tiles=" + tilePalette.size()
				+ ", patterns=" + patterns.length
				+ ", adjacencyEdges=" + edges + ")";
	}

	// ------------------------------------------------------------------

	private static final class PatternKey {
		final int[] tiles;
		final int hash;
		PatternKey(int[] tiles) {
			this.tiles = tiles;
			this.hash = Arrays.hashCode(tiles);
		}
		@Override public boolean equals(Object o) {
			return o instanceof PatternKey && Arrays.equals(tiles, ((PatternKey) o).tiles);
		}
		@Override public int hashCode() { return hash; }
	}

	private static final class IntList {
		int[] data = new int[4];
		int size = 0;
		void add(int v) {
			if(size == data.length) data = Arrays.copyOf(data, data.length * 2);
			data[size++] = v;
		}
	}
}
