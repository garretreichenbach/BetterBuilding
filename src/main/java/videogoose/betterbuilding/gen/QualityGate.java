package videogoose.betterbuilding.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cheap, deterministic geometric checks on a generated {@link VoxelTemplate}.
 * Returns a list of concrete, fixable issues (empty = passed). These run before
 * the expensive VLM critique so broken builds are caught and repaired for free.
 *
 * <p>Targets the common single-shot failure modes: near-empty, degenerate/flat,
 * underusing the requested volume, and the "giant featureless box".
 */
public final class QualityGate {

	private static final int MIN_BLOCKS = 40;
	private static final double MIN_SPAN_FRACTION_XZ = 0.30; // of the requested width/length
	private static final double MIN_SPAN_FRACTION_Y = 0.25;  // of the requested height
	private static final long BOX_MIN_VOLUME = 1500;         // only flag boxiness on sizable builds
	private static final double BOX_FILL_RATIO = 0.72;       // solid / bounding-box volume
	private static final int MIN_DISTINCT_TYPES = 2;         // monochrome-ish if fewer distinct block types

	private QualityGate() {}

	public static List<String> check(VoxelTemplate t) {
		List<String> issues = new ArrayList<>();
		int dx = t.dimX(), dy = t.dimY(), dz = t.dimZ();

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int count = 0;
		Set<Short> types = new HashSet<>();
		for (int z = 0; z < dz; z++) {
			for (int y = 0; y < dy; y++) {
				for (int x = 0; x < dx; x++) {
					short type = t.getType(x, y, z);
					if (type == 0) continue;
					count++;
					types.add(type);
					if (x < minX) minX = x; if (x > maxX) maxX = x;
					if (y < minY) minY = y; if (y > maxY) maxY = y;
					if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
				}
			}
		}

		if (count == 0) {
			issues.add("Empty build — no blocks placed.");
			return issues;
		}

		int spanX = maxX - minX + 1, spanY = maxY - minY + 1, spanZ = maxZ - minZ + 1;

		if (count < MIN_BLOCKS) {
			issues.add("Too few blocks (" + count + ") — build a fuller, more detailed ship.");
		}
		if (spanX < 2 || spanY < 2 || spanZ < 2) {
			issues.add("Build is flat/degenerate on an axis (" + spanX + "x" + spanY + "x" + spanZ
					+ ") — give it real 3D volume.");
		}
		if (spanX < MIN_SPAN_FRACTION_XZ * dx) {
			issues.add("Barely uses the width (spans " + spanX + " of " + dx + ") — widen the hull or wings.");
		}
		if (spanY < MIN_SPAN_FRACTION_Y * dy) {
			issues.add("Barely uses the height (spans " + spanY + " of " + dy + ") — add vertical structure.");
		}
		if (spanZ < MIN_SPAN_FRACTION_XZ * dz) {
			issues.add("Barely uses the length (spans " + spanZ + " of " + dz + ") — extend the hull along Z.");
		}

		if (types.size() < MIN_DISTINCT_TYPES && count >= MIN_BLOCKS) {
			issues.add("Uses only " + types.size() + " block type — add colour/material contrast (panels, "
					+ "accents, a cockpit/light colour from the palette).");
		}

		long bbVol = (long) spanX * spanY * spanZ;
		double fill = bbVol > 0 ? (double) count / bbVol : 0;
		if (bbVol >= BOX_MIN_VOLUME && fill > BOX_FILL_RATIO) {
			issues.add(String.format(
					"Near-solid box (%.0f%% of its bounding box is filled) — hollow the interior and shape the "
					+ "exterior: taper the nose and tail, bevel edges with wedges, and break up flat faces with detail.",
					fill * 100));
		}

		return issues;
	}
}
