package videogoose.betterbuilding;

import api.mod.StarMod;

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
		registerCommands();
	}

	private void registerCommands() {
	}
}