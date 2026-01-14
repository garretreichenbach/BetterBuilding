package thederpgamer.betterbuilding.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.betterbuilding.BetterBuilding;

public class ConfigManager {

	private static final String[] defaultMainConfig = {};
	private static FileConfiguration mainConfig;

	public static void initialize(BetterBuilding instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}
}