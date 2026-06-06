package videogoose.betterbuilding.gen.render;

/**
 * Minimal read-only view of a voxel grid for the {@link Renderer}. Deliberately
 * decoupled from {@code VoxelTemplate} (and thus from the StarMade engine) so the
 * renderer is pure {@code java.awt} and can run/test headless anywhere.
 *
 * <p>Coordinates: X = width, Y = height (up), Z = length. {@link #rgb} is only
 * meaningful where {@link #isSolid} is true.
 */
public interface VoxelView {

	int dimX();

	int dimY();

	int dimZ();

	/** True if the cell holds a (non-air) block; false for air or out of bounds. */
	boolean isSolid(int x, int y, int z);

	/** Representative colour of a solid cell, packed 0xRRGGBB. */
	int rgb(int x, int y, int z);
}
