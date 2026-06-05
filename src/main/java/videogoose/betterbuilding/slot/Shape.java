package videogoose.betterbuilding.slot;

import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;

/**
 * The fixed geometry family of a {@link SlotCell}, decoupled from any concrete
 * StarMade block.
 *
 * <p>Each shape maps onto StarMade's own representation: a {@link BlockStyle}
 * (cube/wedge/corner/tetra/hepta) plus a {@code slab} index (0 = full block,
 * 1 = 3/4, 2 = 1/2, 3 = 1/4). A {@link videogoose.betterbuilding.content.Palette}
 * resolves (slot, shape) back to a concrete block using the same {@code styleIds}
 * / {@code slabIds} lookup that the vanilla FillTool uses.
 */
public enum Shape {

	CUBE(BlockStyle.NORMAL, 0),
	WEDGE(BlockStyle.WEDGE, 0),
	CORNER(BlockStyle.CORNER, 0),
	TETRA(BlockStyle.TETRA, 0),
	HEPTA(BlockStyle.HEPTA, 0),
	SLAB_3Q(BlockStyle.NORMAL, 1),
	SLAB_HALF(BlockStyle.NORMAL, 2),
	SLAB_1Q(BlockStyle.NORMAL, 3);

	/** The StarMade block style this shape corresponds to. */
	public final BlockStyle blockStyle;
	/** Slab index (0 = not a slab; 1/2/3 = 3/4, 1/2, 1/4). */
	public final int slab;

	Shape(BlockStyle blockStyle, int slab) {
		this.blockStyle = blockStyle;
		this.slab = slab;
	}

	public boolean isSlab() {
		return slab != 0;
	}

	/**
	 * Derive the {@code Shape} of a concrete block type. Mirrors the branch order
	 * the vanilla FillTool uses: slabs first, then style families.
	 */
	public static Shape of(short type) {
		ElementInformation info = ElementKeyMap.getInfoFast(type);
		if (info == null) return CUBE;
		if (info.slab != 0) {
			switch (info.slab) {
				case 1: return SLAB_3Q;
				case 2: return SLAB_HALF;
				case 3: return SLAB_1Q;
				default: return CUBE;
			}
		}
		BlockStyle bs = info.blockStyle;
		if (bs == BlockStyle.WEDGE) return WEDGE;
		if (bs == BlockStyle.CORNER) return CORNER;
		if (bs == BlockStyle.TETRA) return TETRA;
		if (bs == BlockStyle.HEPTA) return HEPTA;
		return CUBE;
	}
}
