package thederpgamer.betterbuilding.data.template;

import java.util.*;

/**
 * Simple patch-based 3D synthesizer that generates a new TemplateMetaData by stitching
 * cubic patches sampled from input templates. Raster-first placement. Deterministic by default.
 */
public class TemplateGenerator {

	// Default per-axis cap (32 => 32x32x32 maximum by default)
	public static final int DEFAULT_MAX_DIM = 32;
	// Global toggle for ignoring air (block id == 0) during matching/voting
	public static boolean IGNORE_AIR = true;

	/**
	 * Generate a new TemplateMetaData using the PatchSynthesizer algorithm.
	 * @param inputs list of input templates (must be non-empty)
	 * @param outputDims [x,y,z] dimensions for the generated template (caller-supplied)
	 * @param options generation options (may be null for defaults)
	 * @return generated TemplateMetaData
	 */
	public static TemplateMetaData generate(List<TemplateMetaData> inputs, int[] outputDims, GenerationOptions options) {
		if(inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("inputs must be non-empty");
		if(outputDims == null || outputDims.length != 3) {
			throw new IllegalArgumentException("outputDims must be length 3");
		}
		if(options == null) {
			options = new GenerationOptions();
		}

		for(int d : outputDims) if(d <= 0) throw new IllegalArgumentException("outputDims must be > 0");
		if(outputDims[0] > options.maxDimPerAxis || outputDims[1] > options.maxDimPerAxis || outputDims[2] > options.maxDimPerAxis) {
			throw new IllegalArgumentException("outputDims exceed configured maxDimPerAxis: " + options.maxDimPerAxis);
		}

		int pSize = Math.max(1, options.patchSize);
		int overlap = Math.max(0, Math.min(options.overlap, pSize - 1));
		int stride = Math.max(1, pSize - overlap);

		// Collect candidate patches from inputs
		List<CandidatePatch> candidates = new ArrayList<>();

		outer:
		for(TemplateMetaData t : inputs) {
			int[] dims = t.getDimensions();
			if(dims[0] < pSize || dims[1] < pSize || dims[2] < pSize) continue;
			for(int x = 0; x <= dims[0] - pSize; x++) {
				for(int y = 0; y <= dims[1] - pSize; y++) {
					for(int z = 0; z <= dims[2] - pSize; z++) {
						short[] types = new short[pSize * pSize * pSize];
						byte[] orients = new byte[pSize * pSize * pSize];
						int idx = 0;
						for(int px = 0; px < pSize; px++) {
							for(int py = 0; py < pSize; py++) {
								for(int pz = 0; pz < pSize; pz++) {
									types[idx] = t.getTypeAt(x + px, y + py, z + pz);
									orients[idx] = t.getOrientationAt(x + px, y + py, z + pz);
									idx++;
								}
							}
						}
						candidates.add(new CandidatePatch(pSize, types, orients));
						if(options.maxCandidates > 0 && candidates.size() >= options.maxCandidates) {
							break outer;
						}
					}
				}
			}
		}

		// If no candidates, fall back to majority per-voxel
		if(candidates.isEmpty()) {
			return fillByMajority(inputs, outputDims, options);
		}

		// Deterministic RNG for selection
		Random rng = new Random(options.seed);

		TemplateMetaData out = new TemplateMetaData("generated_" + System.currentTimeMillis(), outputDims);
		int ox = outputDims[0], oy = outputDims[1], oz = outputDims[2];

		for(int x = 0; x < ox; x += stride) {
			for(int y = 0; y < oy; y += stride) {
				for(int z = 0; z < oz; z += stride) {
					// Evaluate candidates by overlap with already-filled voxels in out
					List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
					for(CandidatePatch cp : candidates) {
						double score = 0.0;
						int comparisons = 0;
						// compare overlap region where out is non-air
						for(int px = 0; px < pSize; px++) {
							int gx = x + px;
							if(gx < 0 || gx >= ox) continue;
							for(int py = 0; py < pSize; py++) {
								int gy = y + py;
								if(gy < 0 || gy >= oy) continue;
								for(int pz = 0; pz < pSize; pz++) {
									int gz = z + pz;
									if(gz < 0 || gz >= oz) continue;
									int outType = out.getTypeAt(gx, gy, gz);
									int candType = cp.getType(px, py, pz);
									if(IGNORE_AIR && (outType == 0 || candType == 0)) continue;
									comparisons++;
									if(outType == candType) {
										score += 1.0;
										// orientation bonus if present and matches
										if(out.getOrientationAt(gx, gy, gz) == cp.getOrient(px, py, pz)) {
											score += 0.2;
										}
									}
								}
							}
						}
						// If no comparisons (no overlap yet), give neutral base score 0
						// But encourage patches with more non-air voxels slightly
						if(comparisons == 0) {
							int nonAir = 0;
							for(int v : cp.types) if(v != 0) nonAir++;
							score = nonAir * 0.001; // small bias
						} else {
							score = score / comparisons; // normalize
						}
						scored.add(new ScoredCandidate(cp, score));
					}

					// pick topK - sort descending by score
					Collections.sort(scored, new Comparator<ScoredCandidate>() {
						@Override
						public int compare(ScoredCandidate a, ScoredCandidate b) {
							return Double.compare(b.score, a.score); // descending
						}
					});

					int k = Math.max(1, Math.min(options.topK, scored.size()));
					int chosenIdx = 0;
					if(k == 1 || !options.randomizeTieBreaks) {
						chosenIdx = 0;
					} else {
						// pick uniformly from top-k using deterministic RNG
						chosenIdx = rng.nextInt(k);
					}
					CandidatePatch chosen = scored.get(chosenIdx).candidate;

					// Paste chosen patch into output (only write where out is currently air)
					for(int px = 0; px < pSize; px++) {
						int gx = x + px;
						if(gx < 0 || gx >= ox) continue;
						for(int py = 0; py < pSize; py++) {
							int gy = y + py;
							if(gy < 0 || gy >= oy) continue;
							for(int pz = 0; pz < pSize; pz++) {
								int gz = z + pz;
								if(gz < 0 || gz >= oz) continue;
								int curr = out.getTypeAt(gx, gy, gz);
								short candType = chosen.getType(px, py, pz);
								byte candOrient = chosen.getOrient(px, py, pz);
								if(curr == 0 && candType != 0) {
									out.setTypeAt(gx, gy, gz, candType);
									out.setOrientationAt(gx, gy, gz, candOrient);
								}
							}
						}
					}
				}
			}
		}

		// Final pass: fill remaining air voxels by majority rule
		for(int x2 = 0; x2 < ox; x2++) {
			for(int y2 = 0; y2 < oy; y2++) {
				for(int z2 = 0; z2 < oz; z2++) {
					if(out.getTypeAt(x2, y2, z2) == 0) {
						// majority across inputs mapped coordinates
						short chosenType = majorityTypeAt(inputs, outputDims, x2, y2, z2, options);
						out.setTypeAt(x2, y2, z2, chosenType);
						// orientation: pick most common orientation among inputs that had chosenType
						byte orient = majorityOrientationAt(inputs, outputDims, x2, y2, z2, chosenType, options);
						out.setOrientationAt(x2, y2, z2, orient);
					}
				}
			}
		}

		return out;
	}

	// Map target coordinate to input coordinate by simple scaling and clamping
	private static int mapCoord(int targetCoord, int targetSize, int inputSize) {
		if(inputSize <= 1) return 0;
		int mapped = (int) ((long) targetCoord * inputSize / targetSize);
		if(mapped < 0) mapped = 0;
		if(mapped >= inputSize) mapped = inputSize - 1;
		return mapped;
	}

	// Fallback majority fill when no candidates available
	private static TemplateMetaData fillByMajority(List<TemplateMetaData> inputs, int[] outputDims, GenerationOptions options) {
		TemplateMetaData out = new TemplateMetaData("generated_" + System.currentTimeMillis(), outputDims);
		int ox = outputDims[0], oy = outputDims[1], oz = outputDims[2];
		Random rng = new Random(options.seed);
		for(int x = 0; x < ox; x++) {
			for(int y = 0; y < oy; y++) {
				for(int z = 0; z < oz; z++) {
					short chosenType = majorityTypeAt(inputs, outputDims, x, y, z, options);
					out.setTypeAt(x, y, z, chosenType);
					byte orient = majorityOrientationAt(inputs, outputDims, x, y, z, chosenType, options);
					out.setOrientationAt(x, y, z, orient);
				}
			}
		}
		return out;
	}

	private static short majorityTypeAt(List<TemplateMetaData> inputs, int[] outputDims, int tx, int ty, int tz, GenerationOptions options) {
		Map<Short, Integer> counts = new HashMap<>();
		for(TemplateMetaData t : inputs) {
			int[] dims = t.getDimensions();
			int ix = mapCoord(tx, outputDims[0], dims[0]);
			int iy = mapCoord(ty, outputDims[1], dims[1]);
			int iz = mapCoord(tz, outputDims[2], dims[2]);
			short type = t.getTypeAt(ix, iy, iz);
			if(IGNORE_AIR && type == 0) continue;
			if(counts.containsKey(type)) counts.put(type, counts.get(type) + 1);
			else counts.put(type, 1);
		}
		if(counts.isEmpty()) return 0;
		short best = 0;
		int bestCnt = -1;
		for(Map.Entry<Short, Integer> e : counts.entrySet()) {
			if(e.getValue() > bestCnt) {
				best = e.getKey();
				bestCnt = e.getValue();
			}
		}
		return best;
	}

	private static byte majorityOrientationAt(List<TemplateMetaData> inputs, int[] outputDims, int tx, int ty, int tz, int chosenType, GenerationOptions options) {
		Map<Byte, Integer> counts = new HashMap<>();
		for(TemplateMetaData t : inputs) {
			int[] dims = t.getDimensions();
			int ix = mapCoord(tx, outputDims[0], dims[0]);
			int iy = mapCoord(ty, outputDims[1], dims[1]);
			int iz = mapCoord(tz, outputDims[2], dims[2]);
			int type = t.getTypeAt(ix, iy, iz);
			if(type != chosenType) continue;
			byte o = t.getOrientationAt(ix, iy, iz);
			if(counts.containsKey(o)) counts.put(o, counts.get(o) + 1);
			else counts.put(o, 1);
		}
		if(counts.isEmpty()) return 0;
		byte best = 0;
		int bestCnt = -1;
		for(Map.Entry<Byte, Integer> e : counts.entrySet()) {
			if(e.getValue() > bestCnt) {
				best = e.getKey();
				bestCnt = e.getValue();
			}
		}
		return best;
	}

	public static class GenerationOptions {
		public long seed;
		public int patchSize = 3;
		public int overlap = 1;
		public int topK = 5;
		public boolean randomizeTieBreaks = true;
		/** Max number of candidate patches to collect overall (0 = no cap) */
		public int maxCandidates;
		/** Per-axis maximum dimension allowed for output (default DEFAULT_MAX_DIM) */
		public int maxDimPerAxis = DEFAULT_MAX_DIM;

		public GenerationOptions() {
		}
	}

	private static class CandidatePatch {
		final int size; // patchSize
		final short[] types; // length size*size*size
		final byte[] orients;

		CandidatePatch(int size, short[] types, byte[] orients) {
			this.size = size;
			this.types = types;
			this.orients = orients;
		}

		int getIndex(int x, int y, int z) {
			return x + y * size + z * size * size;
		}

		short getType(int x, int y, int z) {
			return types[getIndex(x, y, z)];
		}

		byte getOrient(int x, int y, int z) {
			return orients[getIndex(x, y, z)];
		}
	}

	private static class ScoredCandidate {
		final CandidatePatch candidate;
		final double score;

		ScoredCandidate(CandidatePatch c, double s) {
			candidate = c;
			score = s;
		}
	}
}

