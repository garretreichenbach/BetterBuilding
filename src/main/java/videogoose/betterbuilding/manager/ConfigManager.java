package videogoose.betterbuilding.manager;

import api.mod.config.FileConfiguration;
import videogoose.betterbuilding.BetterBuilding;

public class ConfigManager {

	private static FileConfiguration mainConfig;

	private static final String[] defaultMainConfig = {
			"lmstudio-url: http__//localhost__1234",
			"lmstudio-model: default",
			"lmstudio-temperature: 0.7",
			"lmstudio-max-tokens: 4096",
			"lmstudio-timeout-ms: 120000"
	};

	public static void initialize(BetterBuilding instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static String getLMStudioUrl() {
		return mainConfig.getConfigurableValue("lmstudio-url", "http__//localhost__1234").replace("__", ":");
	}

	public static String getLMStudioModel() {
		return mainConfig.getConfigurableValue("lmstudio-model", "default");
	}

	public static float getLMStudioTemperature() {
		try {
			return Float.parseFloat(mainConfig.getConfigurableValue("lmstudio-temperature", "0.7"));
		} catch(NumberFormatException e) {
			return 0.7f;
		}
	}

	public static int getLMStudioMaxTokens() {
		try {
			return Integer.parseInt(mainConfig.getConfigurableValue("lmstudio-max-tokens", "4096"));
		} catch(NumberFormatException e) {
			return 4096;
		}
	}

	public static int getLMStudioTimeout() {
		try {
			return Integer.parseInt(mainConfig.getConfigurableValue("lmstudio-timeout-ms", "120000"));
		} catch(NumberFormatException e) {
			return 120000;
		}
	}
}
