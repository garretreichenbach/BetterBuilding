package videogoose.betterbuilding.gen.render;

import videogoose.betterbuilding.gen.VoxelTemplate;

/**
 * Adapts a {@link VoxelTemplate} to the engine-free {@link VoxelView} the
 * {@link Renderer} consumes, resolving per-cell colour via {@link BlockColors}.
 */
public final class VoxelTemplateView implements VoxelView {

	private final VoxelTemplate t;
	private final BlockColors colors;

	public VoxelTemplateView(VoxelTemplate t, BlockColors colors) {
		this.t = t;
		this.colors = colors;
	}

	@Override
	public int dimX() {
		return t.dimX();
	}

	@Override
	public int dimY() {
		return t.dimY();
	}

	@Override
	public int dimZ() {
		return t.dimZ();
	}

	@Override
	public boolean isSolid(int x, int y, int z) {
		return t.getType(x, y, z) != 0;
	}

	@Override
	public int rgb(int x, int y, int z) {
		return colors.colorFor(t.getType(x, y, z));
	}
}
