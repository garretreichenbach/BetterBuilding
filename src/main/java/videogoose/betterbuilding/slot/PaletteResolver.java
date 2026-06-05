package videogoose.betterbuilding.slot;

/**
 * Resolves an abstract (slot, shape) pair to a concrete StarMade block type.
 */
public interface PaletteResolver {

	/**
	 * @return the concrete StarMade block type for the given slot and shape,
	 * or {@code -1} if this resolver has no entry for the slot.
	 */
	short resolve(Slot slot, Shape shape);
}
