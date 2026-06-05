package videogoose.betterbuilding.slot;

/**
 * A single cell in the composer's slot-space grid: a material role plus a
 * geometry family plus a verbatim StarMade orientation.
 *
 * <p>Palette-independent — a {@link videogoose.betterbuilding.content.Palette}
 * turns this into a concrete block at render/export time.
 */
public final class SlotCell {

	public final Slot slot;
	public final Shape shape;
	/** StarMade 5-bit orientation value, preserved verbatim. */
	public final byte orientation;

	public SlotCell(Slot slot, Shape shape, byte orientation) {
		this.slot = slot;
		this.shape = shape;
		this.orientation = orientation;
	}

	@Override
	public String toString() {
		return slot + ":" + shape + "@" + orientation;
	}
}
