package thederpgamer.betterbuilding;

import api.mod.StarMod;
import thederpgamer.betterbuilding.gui.BuildHotbar;
import thederpgamer.betterbuilding.manager.ConfigManager;
import thederpgamer.betterbuilding.manager.EventManager;
import thederpgamer.betterbuilding.manager.HotbarManager;

/**
 * Main class for BetterBuilding StarMade mod.
 *
 * @version 1.0 - [01/21/2021]
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

	//Instance
	private static BetterBuilding instance;

	public BetterBuilding() {
	}

	public static BetterBuilding getInstance() {
		return instance;
	}

	public static void main(String[] args) {
	}

	//Data
	public BuildHotbar buildHotbar;

	@Override
	public void onEnable() {
		instance = this;
		ConfigManager.initialize(this);
		EventManager.registerEvents(this);
		HotbarManager.initialize();
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