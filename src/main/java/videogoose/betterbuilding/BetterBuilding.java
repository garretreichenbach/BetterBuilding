package videogoose.betterbuilding;

import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.command.CaptionCommand;
import videogoose.betterbuilding.command.CaptureCommand;
import videogoose.betterbuilding.command.GenTestCommand;
import videogoose.betterbuilding.command.GenerateCommand;
import videogoose.betterbuilding.command.IndexCommand;
import videogoose.betterbuilding.command.RenderCommand;
import videogoose.betterbuilding.command.SearchCommand;

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
		StarLoader.registerCommand(new GenTestCommand());
		StarLoader.registerCommand(new CaptureCommand());
		StarLoader.registerCommand(new RenderCommand());
		StarLoader.registerCommand(new CaptionCommand());
		StarLoader.registerCommand(new IndexCommand());
		StarLoader.registerCommand(new SearchCommand());
		StarLoader.registerCommand(new GenerateCommand());
	}
}
