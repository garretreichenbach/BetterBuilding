package videogoose.betterbuilding.util;

import api.mod.StarMod;
import api.mod.config.FileConfiguration;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * Mod settings, backed by StarMade's {@link FileConfiguration} system
 * ({@code moddata/BetterBuilding/settings.yml}). Lets users rebind the composer
 * key without a rebuild — StarMade has no rebindable mod-keybind registry, so
 * this config is the next best thing.
 */
public final class BbConfig {

	private static final String DEFAULT_KEY = "MINUS";

	private static int composerKey = Keyboard.KEY_MINUS;

	private BbConfig() {}

	public static void load(StarMod mod) {
		FileConfiguration cfg = mod.getConfig("settings");
		cfg.setComment("composer_key",
				"LWJGL key name to open the composer (e.g. MINUS, GRAVE, F4). "
						+ "Default MINUS (the '-' key) — letters A-Z and most symbols are taken by vanilla StarMade. "
						+ "See keyboard.cfg for the key names StarMade uses.");
		String keyName = cfg.getConfigurableValue("composer_key", DEFAULT_KEY).trim().toUpperCase(Locale.ROOT);
		cfg.saveConfig();

		int idx = Keyboard.getKeyIndex(keyName);
		if (idx == Keyboard.KEY_NONE) {
			mod.logWarning("Unknown composer_key '" + keyName + "' in settings.yml; falling back to " + DEFAULT_KEY + ".");
			composerKey = Keyboard.KEY_MINUS;
		} else {
			composerKey = idx;
		}
	}

	/** LWJGL key code that opens the composer. */
	public static int composerKey() {
		return composerKey;
	}

	/** Human-readable name of the current composer key (for messages/help). */
	public static String composerKeyName() {
		return Keyboard.getKeyName(composerKey);
	}
}
