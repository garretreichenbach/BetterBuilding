package videogoose.betterbuilding;

import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.command.BbCommand;
import videogoose.betterbuilding.util.BbConfig;
import videogoose.betterbuilding.util.BbFiles;

/**
 * Main class for the BetterBuilding StarMade mod — a clientside structure
 * composer. Bootstraps the content folders, registers the {@code /bb} command
 * group and a keybind to open the composer.
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
		BbFiles.init(getSkeleton().getResourcesFolder());
		BbFiles.bootstrap();
		BbConfig.load(this);
		SampleContent.installDefaults(this);
		registerCommands();
		registerKeybind();
		logInfo("BetterBuilding enabled. Content root: " + BbFiles.root().getAbsolutePath()
				+ "; composer key: " + BbConfig.composerKeyName());
	}

	private void registerCommands() {
		StarLoader.registerCommand(new BbCommand(this));
	}

	private void registerKeybind() {
		// Scaffolding: the composer-open keybind is wired here. The handler is an
		// intentional no-op until the composer UI lands in Milestone 3. Matching on
		// the raw key code (not getChar) keeps it stable when Shift is held.
		StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				if (event.isKeyDown() && event.getKey() == BbConfig.composerKey()) {
					// TODO(M3): open the composer when the player is in build mode and not typing.
					onComposerKey();
				}
			}
		}, this);
	}

	/** Hook for the composer keybind. No-op until Milestone 3. */
	private void onComposerKey() {
	}
}
