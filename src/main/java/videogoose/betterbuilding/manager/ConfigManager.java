package videogoose.betterbuilding.manager;

import api.mod.config.FileConfiguration;
import videogoose.betterbuilding.BetterBuilding;

public class ConfigManager {

	private static FileConfiguration mainConfig;

	private static final String[] defaultMainConfig = {
			"provider: lmstudio",
			"lmstudio-host: localhost",
			"lmstudio-port: 1234",
			"lmstudio-model: default",
			"lmstudio-temperature: 0.7",
			"lmstudio-max-tokens: 64000",
			"lmstudio-timeout-ms: 120000",
			"ollama-host: localhost",
			"ollama-port: 11434",
			"ollama-model: gemma4"
	};

	public static void initialize(BetterBuilding instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);
	}

	public static String getProvider() {
		return mainConfig.getConfigurableValue("provider", "lmstudio").toLowerCase().trim();
	}

	public static String getLMStudioUrl() {
		String host = mainConfig.getConfigurableValue("lmstudio-host", "localhost").trim();
		int port = getLMStudioPort();
		return "http://" + host + ":" + port;
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
			return Integer.parseInt(mainConfig.getConfigurableValue("lmstudio-max-tokens", "64000"));
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

	public static String getOllamaUrl() {
		String host = mainConfig.getConfigurableValue("ollama-host", "localhost").trim();
		int port = getOllamaPort();
		return "http://" + host + ":" + port;
	}

	public static String getOllamaModel() {
		return mainConfig.getConfigurableValue("ollama-model", "gemma4");
	}

	private static int getLMStudioPort() {
		try {
			return Integer.parseInt(mainConfig.getConfigurableValue("lmstudio-port", "1234").trim());
		} catch(NumberFormatException e) {
			return 1234;
		}
	}

	private static int getOllamaPort() {
		try {
			return Integer.parseInt(mainConfig.getConfigurableValue("ollama-port", "11434").trim());
		} catch(NumberFormatException e) {
			return 11434;
		}
	}
}
