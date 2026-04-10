package videogoose.betterbuilding.data.wfc;

import videogoose.betterbuilding.data.template.TemplateMetaData;

import java.util.Arrays;
import java.util.Random;

/**
 * Overlapping-pattern Wave Function Collapse over a 3D grid, using
 * Gumin-style support counting for propagation.
 *
 * Each cell of the WFC grid holds a possibility set of pattern indices. After
 * the grid is fully collapsed, voxel (x,y,z) of the output is read from the
 * (0,0,0) corner of the pattern at cell (x,y,z). Adjacent cells' patterns
 * overlap on a (N-1)-thick slab, and the compatibility tables enforce that
 * those slabs match — which is exactly what makes the readout coherent.
 *
 * The boundary is left open (no init pruning). Patterns near the corpus
 * boundary already encode "transitions to air" because the ruleset is built
 * from air-padded templates, so the solver naturally produces clean edges
 * without us forcing it.
 *
 * Propagation algorithm:
 *
 *   For every triple (cell C, pattern P, axis A) we keep an integer
 *   `support[C][P][A]` which counts how many patterns at C+A are still
 *   considered legal neighbors of P under the +A direction. Initially this
 *   is just popcount(allowed[A][P]) — every pattern is alive everywhere.
 *
 *   When pattern P is removed from cell C, we walk every axis A and look at
 *   the neighbor N = C+A. For every pattern Q ∈ allowed[A][P] (i.e. every
 *   pattern Q at N that used to count P as one of its valid -A neighbors),
 *   we decrement support[N][Q][¬A]. If that count drops to zero, Q has no
 *   more valid -A neighbors at C and must be removed from N — which queues
 *   another removal, and the cascade continues.
 *
 *   This replaces the O(P × W) per-step union recomputation of the naive
 *   propagator with O(touched_patterns × axis_compatibles), which for sparse
 *   rulesets is dramatically faster.
 *
 * Memory cost: 6 × cellCount × patternCount × 2 bytes (one short per support
 * triple). For 1152 cells × 10K patterns that's ~138 MB. Larger volumes
 * blow this up quickly — see the budget check in {@link #generate}.
 *
 * Entropy is maintained incrementally per cell as a running
 * (sumFreq, sumFreqLogFreq) pair, so picking the lowest-entropy cell
 * doesn't require an O(P) rescan after every removal.
 */
public final class WfcOverlappingGenerator {

	/** Hard cap on the support array — refuses to allocate above this. */
	private static final long MAX_SUPPORT_BYTES = 1_500_000_000L;

	private final WfcPatternRuleset ruleset;
	private final int sizeX, sizeY, sizeZ;
	private final int cellCount;
	private final int patternCount;
	private final int wordsPerBitset;
	private final long seed;
	private final int maxRestarts;

	// Live patterns at each cell — used for entropy / weighted choice / readout.
	private long[] possibilities;
	private int[] remaining;
	private double[] entropy;

	// Incremental entropy state per cell.
	private double[] sumFreq;
	private double[] sumFreqLogFreq;

	// Cached f * log(f) per pattern (constant across the run).
	private double[] freqLogFreq;

	// support[((axis * cellCount) + cell) * patternCount + pattern]
	private short[] support;

	// Pre-computed initial support per (axis, pattern) = popcount(allowed[axis][p]).
	private short[][] initialSupport;

	// Removal stack: packed (cell << 32) | pattern.
	private long[] stack;
	private int stackTop;

	private Random rng;

	public WfcOverlappingGenerator(WfcPatternRuleset ruleset, int sizeX, int sizeY, int sizeZ, long seed, int maxRestarts) {
		if(sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("WFC dims must be positive");
		}
		this.ruleset = ruleset;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.cellCount = sizeX * sizeY * sizeZ;
		this.patternCount = ruleset.patternCount();
		this.wordsPerBitset = ruleset.wordsPerBitset();
		this.seed = seed;
		this.maxRestarts = maxRestarts;
	}

	public TemplateMetaData generate(String name) {
		long supportBytes = 6L * cellCount * (long) patternCount * 2L;
		if(supportBytes > MAX_SUPPORT_BYTES) {
			throw new IllegalStateException("WFC support array would need ~"
					+ (supportBytes >> 20) + " MB (cells=" + cellCount + ", patterns=" + patternCount
					+ "). Reduce grid size or pass -minfreq to drop rare patterns.");
		}

		precomputeStaticTables();

		this.rng = new Random(seed);
		for(int attempt = 0; attempt <= maxRestarts; attempt++) {
			initState();
			if(solve()) return materialize(name);
			this.rng = new Random(seed + attempt + 1L);
		}
		throw new IllegalStateException("Overlapping WFC failed to converge after "
				+ (maxRestarts + 1) + " attempts. Try a smaller volume, more example templates, "
				+ "or a higher restart cap.");
	}

	// ------------------------------------------------------------------
	// Static precompute (once per generate() call, reused across restarts)
	// ------------------------------------------------------------------

	private void precomputeStaticTables() {
		freqLogFreq = new double[patternCount];
		for(int p = 0; p < patternCount; p++) {
			int f = ruleset.frequency(p);
			freqLogFreq[p] = (f > 0) ? f * Math.log(f) : 0.0;
		}

		initialSupport = new short[WfcPatternRuleset.AXIS_COUNT][patternCount];
		for(int axis = 0; axis < WfcPatternRuleset.AXIS_COUNT; axis++) {
			for(int p = 0; p < patternCount; p++) {
				long[] bits = ruleset.allowedNeighbors(axis, p);
				int count = 0;
				for(long w : bits) count += Long.bitCount(w);
				initialSupport[axis][p] = (short) Math.min(count, Short.MAX_VALUE);
			}
		}
	}

	// ------------------------------------------------------------------
	// Per-attempt state init
	// ------------------------------------------------------------------

	private void initState() {
		possibilities = new long[cellCount * wordsPerBitset];
		remaining = new int[cellCount];
		entropy = new double[cellCount];
		sumFreq = new double[cellCount];
		sumFreqLogFreq = new double[cellCount];
		support = new short[WfcPatternRuleset.AXIS_COUNT * cellCount * patternCount];
		stack = new long[Math.max(64, patternCount)];
		stackTop = 0;

		// All patterns alive in every cell.
		long fullLastWordMask;
		int leftover = patternCount & 63;
		if(leftover == 0) fullLastWordMask = -1L;
		else fullLastWordMask = (1L << leftover) - 1L;

		double initSumFreq = 0.0;
		double initSumFLF = 0.0;
		for(int p = 0; p < patternCount; p++) {
			int f = ruleset.frequency(p);
			if(f > 0) {
				initSumFreq += f;
				initSumFLF += freqLogFreq[p];
			}
		}
		double initEntropy = (initSumFreq > 0)
				? Math.log(initSumFreq) - initSumFLF / initSumFreq
				: 0.0;

		for(int c = 0; c < cellCount; c++) {
			int base = c * wordsPerBitset;
			for(int w = 0; w < wordsPerBitset - 1; w++) possibilities[base + w] = -1L;
			possibilities[base + wordsPerBitset - 1] = fullLastWordMask;
			remaining[c] = patternCount;
			sumFreq[c] = initSumFreq;
			sumFreqLogFreq[c] = initSumFLF;
			entropy[c] = initEntropy;
		}

		// Seed the support array. For in-grid neighbors, support is the
		// initial popcount; for out-of-grid neighbors we set it to MAX_VALUE
		// so the propagator never tries to take it to zero (open boundary).
		for(int axis = 0; axis < WfcPatternRuleset.AXIS_COUNT; axis++) {
			int dxA = WfcPatternRuleset.dx(axis);
			int dyA = WfcPatternRuleset.dy(axis);
			int dzA = WfcPatternRuleset.dz(axis);
			short[] init = initialSupport[axis];
			for(int z = 0; z < sizeZ; z++) {
				for(int y = 0; y < sizeY; y++) {
					for(int x = 0; x < sizeX; x++) {
						int c = cellIndex(x, y, z);
						int sBase = (axis * cellCount + c) * patternCount;
						boolean inGrid = (x + dxA >= 0 && y + dyA >= 0 && z + dzA >= 0
								&& x + dxA < sizeX && y + dyA < sizeY && z + dzA < sizeZ);
						if(inGrid) {
							System.arraycopy(init, 0, support, sBase, patternCount);
						} else {
							Arrays.fill(support, sBase, sBase + patternCount, Short.MAX_VALUE);
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Main loop
	// ------------------------------------------------------------------

	private boolean solve() {
		while(true) {
			int target = pickLowestEntropyCell();
			if(target == -2) return false;
			if(target == -1) return true;

			int chosen = weightedChoice(target);
			if(!collapseTo(target, chosen)) return false;
			if(!propagate()) return false;
		}
	}

	private int pickLowestEntropyCell() {
		double bestE = Double.POSITIVE_INFINITY;
		int best = -1;
		for(int c = 0; c < cellCount; c++) {
			int r = remaining[c];
			if(r == 0) return -2;
			if(r == 1) continue;
			double e = entropy[c] + rng.nextDouble() * 1e-6;
			if(e < bestE) {
				bestE = e;
				best = c;
			}
		}
		return best;
	}

	private int weightedChoice(int cell) {
		int base = cell * wordsPerBitset;
		long total = 0;
		for(int w = 0; w < wordsPerBitset; w++) {
			long word = possibilities[base + w];
			while(word != 0) {
				int bit = Long.numberOfTrailingZeros(word);
				int p = (w << 6) | bit;
				word &= word - 1;
				total += ruleset.frequency(p);
			}
		}
		if(total <= 0) {
			int n = remaining[cell];
			int pick = rng.nextInt(Math.max(1, n));
			for(int w = 0; w < wordsPerBitset; w++) {
				long word = possibilities[base + w];
				while(word != 0) {
					int bit = Long.numberOfTrailingZeros(word);
					int p = (w << 6) | bit;
					word &= word - 1;
					if(pick == 0) return p;
					pick--;
				}
			}
			return -1;
		}
		long r = (rng.nextLong() & Long.MAX_VALUE) % total;
		long acc = 0;
		for(int w = 0; w < wordsPerBitset; w++) {
			long word = possibilities[base + w];
			while(word != 0) {
				int bit = Long.numberOfTrailingZeros(word);
				int p = (w << 6) | bit;
				word &= word - 1;
				acc += ruleset.frequency(p);
				if(r < acc) return p;
			}
		}
		return -1;
	}

	private boolean collapseTo(int cell, int chosenPattern) {
		int base = cell * wordsPerBitset;
		// Remove every pattern except the chosen one.
		for(int w = 0; w < wordsPerBitset; w++) {
			long word = possibilities[base + w];
			while(word != 0) {
				int bit = Long.numberOfTrailingZeros(word);
				int p = (w << 6) | bit;
				word &= word - 1;
				if(p == chosenPattern) continue;
				if(!banPattern(cell, p)) return false;
			}
		}
		return true;
	}

	/**
	 * Removes the pattern from the cell, updates entropy, and pushes the
	 * removal onto the propagation stack. No-op if the pattern is already
	 * gone. Returns false if the cell is now empty (contradiction).
	 */
	private boolean banPattern(int cell, int pattern) {
		int base = cell * wordsPerBitset;
		int wIdx = base + (pattern >>> 6);
		long mask = 1L << (pattern & 63);
		if((possibilities[wIdx] & mask) == 0L) return true; // already removed
		possibilities[wIdx] &= ~mask;
		remaining[cell]--;

		int f = ruleset.frequency(pattern);
		if(f > 0) {
			sumFreq[cell] -= f;
			sumFreqLogFreq[cell] -= freqLogFreq[pattern];
			if(sumFreq[cell] > 0) {
				entropy[cell] = Math.log(sumFreq[cell]) - sumFreqLogFreq[cell] / sumFreq[cell];
			} else {
				entropy[cell] = 0.0;
			}
		}

		if(remaining[cell] == 0) return false;
		pushRemoval(cell, pattern);
		return true;
	}

	private void pushRemoval(int cell, int pattern) {
		if(stackTop == stack.length) {
			stack = Arrays.copyOf(stack, stack.length * 2);
		}
		stack[stackTop++] = ((long) cell << 32) | (pattern & 0xFFFFFFFFL);
	}

	private boolean propagate() {
		while(stackTop > 0) {
			long packed = stack[--stackTop];
			int cell = (int) (packed >>> 32);
			int pattern = (int) (packed & 0xFFFFFFFFL);
			int cx = cell % sizeX;
			int cy = (cell / sizeX) % sizeY;
			int cz = cell / (sizeX * sizeY);

			for(int axis = 0; axis < WfcPatternRuleset.AXIS_COUNT; axis++) {
				int nx = cx + WfcPatternRuleset.dx(axis);
				int ny = cy + WfcPatternRuleset.dy(axis);
				int nz = cz + WfcPatternRuleset.dz(axis);
				if(nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) continue;

				int neighbor = cellIndex(nx, ny, nz);
				int oppAxis = axis ^ 1;
				int neighborSupportBase = (oppAxis * cellCount + neighbor) * patternCount;

				// Walk every Q ∈ allowed[axis][pattern]: each such Q at the
				// neighbor used to count `pattern` as a valid (-axis)-neighbor.
				long[] toUpdate = ruleset.allowedNeighbors(axis, pattern);
				for(int w = 0; w < wordsPerBitset; w++) {
					long word = toUpdate[w];
					while(word != 0) {
						int bit = Long.numberOfTrailingZeros(word);
						int q = (w << 6) | bit;
						word &= word - 1;

						int idx = neighborSupportBase + q;
						short s = (short) (support[idx] - 1);
						support[idx] = s;
						if(s == 0) {
							// Q has no more (-axis) supporters at the neighbor — kill it.
							if(!banPattern(neighbor, q)) return false;
						}
					}
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
					int chosen = -1;
					for(int w = 0; w < wordsPerBitset; w++) {
						long word = possibilities[base + w];
						if(word != 0) {
							chosen = (w << 6) | Long.numberOfTrailingZeros(word);
							break;
						}
					}
					if(chosen < 0) continue;
					int[] tiles = ruleset.pattern(chosen);
					int tileIdx = tiles[0]; // (0,0,0) corner
					if(tileIdx <= 0) continue;
					WfcTile tile = ruleset.tile(tileIdx);
					if(tile.isAir()) continue;
					out.setTypeAt(x, y, z, tile.getType());
					out.setOrientationAt(x, y, z, tile.getOrientation());
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------

	private int cellIndex(int x, int y, int z) {
		return x + y * sizeX + z * sizeX * sizeY;
	}
}
