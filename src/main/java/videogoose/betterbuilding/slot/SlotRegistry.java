package videogoose.betterbuilding.slot;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of {@link Slot}s. Built-in slots are pre-registered; styles and
 * palettes may register additional custom slots on load.
 */
public final class SlotRegistry {

	private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();

	public static final Slot PRIMARY_HULL = register("PRIMARY_HULL");
	public static final Slot SECONDARY_HULL = register("SECONDARY_HULL");
	public static final Slot ACCENT = register("ACCENT");
	public static final Slot GLASS = register("GLASS");
	public static final Slot LIGHT = register("LIGHT");
	public static final Slot SYSTEM = register("SYSTEM");

	private SlotRegistry() {}

	/** Register (or fetch the existing) slot with the given name. */
	public static Slot register(String name) {
		String key = name.toUpperCase(Locale.ROOT);
		return SLOTS.computeIfAbsent(key, Slot::new);
	}

	/** Alias for {@link #register(String)} — reads better at call sites that may create. */
	public static Slot getOrCreate(String name) {
		return register(name);
	}

	/** @return the registered slot, or {@code null} if not registered. */
	public static Slot get(String name) {
		return SLOTS.get(name.toUpperCase(Locale.ROOT));
	}

	public static Collection<Slot> all() {
		return SLOTS.values();
	}
}
