package videogoose.betterbuilding.gen;

import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SegmentData;

/**
 * Reads StarMade entities (ships/stations the engine already has loaded) into a
 * {@link VoxelTemplate}, the in-memory grid the rest of the pipeline works on.
 *
 * <p>Strategy (Phase 1, "spawn-and-read"): rather than re-implement the {@code .smd3}
 * region/Tag/SegmentData4Byte format by hand (the source of past bugs), we capture
 * an already-loaded {@link SegmentController} with the engine's own
 * {@link CopyArea#copyArea} — block type and orientation come through losslessly.
 * {@link VoxelTemplate#fromCopyArea} normalizes to a 0-origin grid.
 *
 * <p>A future headless reader (driving {@code SegmentRegionFileNew} directly for
 * batch processing without the game running) can slot in behind this same class.
 */
public final class BlueprintReader {

	/**
	 * Max grid volume we'll capture into a dense {@link VoxelTemplate}. Capital
	 * titans exceed this; dense storage would OOM. They're skipped for now and are
	 * a target for the future sparse/headless path. ~64M cells ≈ 192 MB dense.
	 */
	private static final long MAX_VOLUME = 64_000_000L;

	private BlueprintReader() {}

	/**
	 * Capture a loaded {@link SegmentController} into a {@link VoxelTemplate}.
	 *
	 * @throws IllegalStateException if the entity is empty or too large to capture densely.
	 */
	public static VoxelTemplate fromSegmentController(String name, SegmentController sc) {
		// getMinPos/getMaxPos are in SEGMENT coordinates; block coords = seg * SEG.
		// Span the full extent of the boundary segments (+SEG-1 on the max side).
		int seg = SegmentData.SEG;
		Vector3i segMin = sc.getMinPos();
		Vector3i segMax = sc.getMaxPos();
		Vector3i min = new Vector3i(segMin.x * seg, segMin.y * seg, segMin.z * seg);
		Vector3i max = new Vector3i(segMax.x * seg + (seg - 1),
				segMax.y * seg + (seg - 1),
				segMax.z * seg + (seg - 1));

		long volume = (long) (max.x - min.x + 1) * (max.y - min.y + 1) * (max.z - min.z + 1);
		if (volume <= 0) {
			throw new IllegalStateException("entity has no blocks (empty bounds " + min + " .. " + max + ")");
		}
		if (volume > MAX_VOLUME) {
			throw new IllegalStateException("entity too large to capture densely: " + volume
					+ " cells (cap " + MAX_VOLUME + "). Needs the future sparse/headless reader.");
		}

		CopyArea area = new CopyArea();
		area.copyArea(sc, min, max);
		return VoxelTemplate.fromCopyArea(name, area);
	}
}
