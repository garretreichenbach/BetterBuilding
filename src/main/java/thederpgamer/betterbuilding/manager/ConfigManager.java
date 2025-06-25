package thederpgamer.betterbuilding.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.betterbuilding.BetterBuilding;

public class ConfigManager {

	private static FileConfiguration mainConfig;
	private static final String[] defaultMainConfig = {
			"global-hotbars: true",
			"llm-enabled: true",
			"llm-model: google/gemma-3-12b",
			"llm-url: http://192.168.1.197:8000/v1/chat/completions",
			"llm-api-key: lmstudio"
	};

	public static void initialize(BetterBuilding instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}
}