package videogoose.betterbuilding.data.wfc;

import videogoose.betterbuilding.data.template.TemplateMetaData;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Block-level overlapping Wave Function Collapse over a 3D grid.
 *
 * Each cell starts with every tile in the ruleset's palette as a possibility.
 * We repeatedly:
 *   1. pick the cell with lowest non-zero entropy (frequency-weighted Shannon),
 *   2. collapse it to a single tile drawn from the remaining options,
 *   3. propagate the new constraint outward by AC-3 — for each neighbor cell,
 *      remove any option that cannot legally sit next to *any* surviving option
 *      in the just-updated cell on the appropriate axis.
 *
 * On contradiction (a cell with zero options) we restart the whole grid up to
 * `maxRestarts` times. Restart is simpler and good enough for v1; full
 * backtracking can come later if we hit corpora that need it.
 *
 * Out-of-bounds is treated as air (tile index 0). The boundary doesn't pin
 * cells to air directly — instead, propagation from the implicit air tile
 * outside the grid is folded into initial constraints on edge/face/corner
 * cells, so a tile that "needs solid neighbors on all sides" never gets
 * placed in a corner.
 */
public final class WfcGenerator {

	private final WfcRuleset ruleset;
	private final int sizeX, sizeY, sizeZ;
	private final int cellCount;
	private final int paletteSize;
	private final int wordsPerBitset;
	private final long seed;
	private final int maxRestarts;

	/**
	 * possibilities[cellIndex * wordsPerBitset .. +wordsPerBitset] is the bitset
	 * of tile indices still allowed at that cell.
	 */
	private long[] possibilities;
	/** Number of options remaining at each cell (popcount of its bitset). */
	private int[] remaining;
	/** Cached frequency-weighted Shannon entropy at each cell. */
	private double[] entropy;

	private Random rng;

	public WfcGenerator(WfcRuleset ruleset, int sizeX, int sizeY, int sizeZ, long seed, int maxRestarts) {
		if(sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("WFC dims must be positive");
		}
		this.ruleset = ruleset;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.cellCount = sizeX * sizeY * sizeZ;
		this.paletteSize = ruleset.paletteSize();
		this.wordsPerBitset = ruleset.wordsPerBitset();
		this.seed = seed;
		this.maxRestarts = maxRestarts;
	}

	/**
	 * Run the solver. Returns a freshly-allocated TemplateMetaData with the
	 * collapsed result, or throws if every restart attempt hit a contradiction.
	 */
	public TemplateMetaData generate(String name) {
		this.rng = new Random(seed);

		for(int attempt = 0; attempt <= maxRestarts; attempt++) {
			boolean initOk = initState();
			if(initOk && solve()) {
				return materialize(name);
			}
			// Reseed slightly so the next attempt isn't an exact replay
			this.rng = new Random(seed + attempt + 1L);
		}
		throw new IllegalStateException("WFC failed to converge after " + (maxRestarts + 1)
				+ " attempts. Try a smaller volume, more example templates, or a higher restart cap.");
	}

	// ------------------------------------------------------------------
	// State init
	// ------------------------------------------------------------------

	private boolean initState() {
		possibilities = new long[cellCount * wordsPerBitset];
		remaining = new int[cellCount];
		entropy = new double[cellCount];

		// Every cell starts with every tile.
		long fullLastWordMask;
		int leftover = paletteSize & 63;
		if(leftover == 0) fullLastWordMask = -1L;
		else fullLastWordMask = (1L << leftover) - 1L;

		for(int c = 0; c < cellCount; c++) {
			int base = c * wordsPerBitset;
			for(int w = 0; w < wordsPerBitset - 1; w++) possibilities[base + w] = -1L;
			possibilities[base + wordsPerBitset - 1] = fullLastWordMask;
			remaining[c] = paletteSize;
			entropy[c] = computeEntropyOf(base);
		}

		// Seed propagation from the implicit air at the boundary.
		// For every face cell, prune options that can't have air on the
		// outside-facing axis. Then propagate those pruning decisions inward
		// so interior cells see the constraint before solving begins.
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for(int z = 0; z < sizeZ; z++) {
			for(int y = 0; y < sizeY; y++) {
				for(int x = 0; x < sizeX; x++) {
					boolean onBoundary = (x == 0 || y == 0 || z == 0
							|| x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1);
					if(!onBoundary) continue;
					int c = cellIndex(x, y, z);
					int base = c * wordsPerBitset;
					boolean changed = false;

					for(int axis = 0; axis < WfcRuleset.AXIS_COUNT; axis++) {
						int nx = x + WfcRuleset.dx(axis);
						int ny = y + WfcRuleset.dy(axis);
						int nz = z + WfcRuleset.dz(axis);
						if(nx >= 0 && ny >= 0 && nz >= 0 && nx < sizeX && ny < sizeY && nz < sizeZ) continue;
						// Outside is air (tile 0). Keep only tiles whose
						// "+axis allowed neighbors" set contains air.
						for(int t = 0; t < paletteSize; t++) {
							if(!hasBit(possibilities, base, t)) continue;
							long[] allowed = ruleset.allowedNeighbors(axis, t);
							if((allowed[0] & 1L) == 0L) {
								clearBit(possibilities, base, t);
								remaining[c]--;
								changed = true;
							}
						}
					}
					if(changed) {
						entropy[c] = computeEntropyOf(base);
						if(remaining[c] == 0) return false;
						queue.add(c);
					}
				}
			}
		}

		return propagate(queue);
	}

	// ------------------------------------------------------------------
	// Main loop
	// ------------------------------------------------------------------

	private boolean solve() {
		while(true) {
			int target = pickLowestEntropyCell();
			if(target == -2) return false; // contradiction observed
			if(target == -1) return true;   // every cell collapsed

			int chosenTile = weightedChoice(target);
			collapseTo(target, chosenTile);

			ArrayDeque<Integer> propQueue = new ArrayDeque<>();
			propQueue.add(target);
			if(!propagate(propQueue)) return false;
		}
	}

	/**
	 * @return -2 if any cell has zero options (contradiction), -1 if every cell
	 *         is already collapsed, otherwise the index of the lowest-entropy
	 *         cell with more than one option.
	 */
	private int pickLowestEntropyCell() {
		double bestEntropy = Double.POSITIVE_INFINITY;
		int best = -1;
		for(int c = 0; c < cellCount; c++) {
			int r = remaining[c];
			if(r == 0) return -2;
			if(r == 1) continue;
			// Tiny noise tiebreaker so equal-entropy cells don't always pick the same one.
			double e = entropy[c] + rng.nextDouble() * 1e-6;
			if(e < bestEntropy) {
				bestEntropy = e;
				best = c;
			}
		}
		return best;
	}

	private int weightedChoice(int cellIdx) {
		int base = cellIdx * wordsPerBitset;
		long total = 0;
		for(int t = 0; t < paletteSize; t++) {
			if(hasBit(possibilities, base, t)) total += ruleset.frequency(t);
		}
		if(total <= 0) {
			// Fall back to uniform if all options have zero frequency (shouldn't happen).
			int countLeft = remaining[cellIdx];
			int pick = rng.nextInt(Math.max(1, countLeft));
			for(int t = 0; t < paletteSize; t++) {
				if(hasBit(possibilities, base, t)) {
					if(pick == 0) return t;
					pick--;
				}
			}
			return -1;
		}
		long r = (rng.nextLong() & Long.MAX_VALUE) % total;
		long acc = 0;
		for(int t = 0; t < paletteSize; t++) {
			if(!hasBit(possibilities, base, t)) continue;
			acc += ruleset.frequency(t);
			if(r < acc) return t;
		}
		return -1;
	}

	private void collapseTo(int cellIdx, int tile) {
		int base = cellIdx * wordsPerBitset;
		for(int w = 0; w < wordsPerBitset; w++) possibilities[base + w] = 0L;
		setBit(possibilities, base, tile);
		remaining[cellIdx] = 1;
		entropy[cellIdx] = 0.0;
	}

	// ------------------------------------------------------------------
	// AC-3 style propagation
	// ------------------------------------------------------------------

	private boolean propagate(ArrayDeque<Integer> queue) {
		while(!queue.isEmpty()) {
			int c = queue.poll();
			int cx = c % sizeX;
			int cy = (c / sizeX) % sizeY;
			int cz = c / (sizeX * sizeY);
			int base = c * wordsPerBitset;

			for(int axis = 0; axis < WfcRuleset.AXIS_COUNT; axis++) {
				int nx = cx + WfcRuleset.dx(axis);
				int ny = cy + WfcRuleset.dy(axis);
				int nz = cz + WfcRuleset.dz(axis);
				if(nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) continue;

				int neighbor = cellIndex(nx, ny, nz);
				int nbase = neighbor * wordsPerBitset;

				// Build the union of "tiles allowed in the +axis direction
				// from any surviving option in c" — that's the legal set for
				// the neighbor cell on this side.
				long[] union = new long[wordsPerBitset];
				for(int t = 0; t < paletteSize; t++) {
					if(!hasBit(possibilities, base, t)) continue;
					long[] allowed = ruleset.allowedNeighbors(axis, t);
					for(int w = 0; w < wordsPerBitset; w++) union[w] |= allowed[w];
				}

				// Intersect neighbor's possibilities with the union.
				boolean changed = false;
				int newCount = 0;
				for(int w = 0; w < wordsPerBitset; w++) {
					long before = possibilities[nbase + w];
					long after = before & union[w];
					if(after != before) changed = true;
					possibilities[nbase + w] = after;
					newCount += Long.bitCount(after);
				}

				if(changed) {
					remaining[neighbor] = newCount;
					if(newCount == 0) return false; // contradiction
					entropy[neighbor] = computeEntropyOf(nbase);
					queue.add(neighbor);
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Output
	// ------------------------------------------------------------------

	private TemplateMetaData materialize(String name) {
		TemplateMetaData out = new TemplateMetaData(name, new int[]{sizeX, sizeY, sizeZ});
		for(int z = 0; z < sizeZ; z++) {
			for(int y = 0; y < sizeY; y++) {
				for(int x = 0; x < sizeX; x++) {
					int c = cellIndex(x, y, z);
					int base = c * wordsPerBitset;
					int onlyTile = -1;
					for(int t = 0; t < paletteSize; t++) {
						if(hasBit(possibilities, base, t)) { onlyTile = t; break; }
					}
					if(onlyTile <= 0) continue; // air or empty
					WfcTile tile = ruleset.tile(onlyTile);
					if(tile.isAir()) continue;
					out.setTypeAt(x, y, z, tile.getType());
					out.setOrientationAt(x, y, z, tile.getOrientation());
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private int cellIndex(int x, int y, int z) {
		return x + y * sizeX + z * sizeX * sizeY;
	}

	private double computeEntropyOf(int base) {
		long totalFreq = 0;
		for(int t = 0; t < paletteSize; t++) {
			if(hasBit(possibilities, base, t)) totalFreq += ruleset.frequency(t);
		}
		if(totalFreq <= 0) return 0.0;
		double logTotal = Math.log(totalFreq);
		double sum = 0.0;
		for(int t = 0; t < paletteSize; t++) {
			if(!hasBit(possibilities, base, t)) continue;
			int f = ruleset.frequency(t);
			if(f <= 0) continue;
			sum += f * (logTotal - Math.log(f));
		}
		return sum / totalFreq;
	}

	private static boolean hasBit(long[] bits, int base, int i) {
		return (bits[base + (i >>> 6)] & (1L << (i & 63))) != 0L;
	}

	private static void setBit(long[] bits, int base, int i) {
		bits[base + (i >>> 6)] |= 1L << (i & 63);
	}

	private static void clearBit(long[] bits, int base, int i) {
		bits[base + (i >>> 6)] &= ~(1L << (i & 63));
	}
}
