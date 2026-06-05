package videogoose.betterbuilding.slot;

import java.util.Locale;

/**
 * A named material role (e.g. {@code PRIMARY_HULL}, {@code ACCENT}, {@code GLASS}).
 *
 * <p>Slots are registry-based, not a fixed enum: styles and palettes may declare
 * custom slots. Obtain instances through {@link SlotRegistry}; names are
 * case-insensitive and canonicalised to upper case.
 */
public final class Slot {

	private final String name;

	/** Package-private — construct slots via {@link SlotRegistry}. */
	Slot(String name) {
		this.name = name.toUpperCase(Locale.ROOT);
	}

	public String name() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Slot && ((Slot) o).name.equals(name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return name;
	}
}
