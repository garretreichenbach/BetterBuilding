package videogoose.betterbuilding;

import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.data.commands.ExportTrainingDataCommand;
import videogoose.betterbuilding.data.commands.GenerateTemplateCommand;
import videogoose.betterbuilding.data.commands.GenerateWfcCommand;
import videogoose.betterbuilding.manager.ConfigManager;

/**
 * Main class for BetterBuilding StarMade mod.
 */
public class BetterBuilding extends StarMod {

	private static BetterBuilding instance;

	public BetterBuilding() {
	}

	public static BetterBuilding getInstance() {
		return instance;
	}

	public static void main(String[] args) {
	}

	@Override
	public void onEnable() {
		instance = this;
		ConfigManager.initialize(this);
		registerCommands();
	}

	private void registerCommands() {
		StarLoader.registerCommand(new GenerateTemplateCommand());
		StarLoader.registerCommand(new ExportTrainingDataCommand());
		StarLoader.registerCommand(new GenerateWfcCommand());
	}

	@Override
	public void logInfo(String message) {
		super.logInfo(message);
		System.out.println("[BetterBuilding][INFO]" + message);
	}

	@Override
	public void logWarning(String message) {
		super.logWarning(message);
		System.err.println("[BetterBuilding][WARNING]" + message);
	}

	@Override
	public void logException(String message, Exception exception) {
		super.logException(message, exception);
		System.err.println("[BetterBuilding][EXCEPTION]" + message);
		exception.printStackTrace();
	}

	@Override
	public void logFatal(String message, Exception exception) {
		System.err.println("[BetterBuilding][FATAL]" + message);
		exception.printStackTrace();
		super.logFatal(message, exception);
	}
}