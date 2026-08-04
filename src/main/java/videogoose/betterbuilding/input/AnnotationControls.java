package videogoose.betterbuilding.input;

import org.lwjgl.input.Keyboard;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import api.utils.gui.SimplePlayerTextInput;
import videogoose.betterbuilding.annotation.Anchor;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;
import videogoose.betterbuilding.annotation.LabelSize;

/**
 * Keybinds for creating and adjusting annotations.
 * <p>
 * Bindings go through {@link KeyboardMappings#registerMapping}, so they show up in the
 * game's own controls screen and respect user remapping, rather than hard-coding keys.
 * <p>
 * This is deliberately not built on StarLoader's {@code CommandInterface}: those are
 * dispatched server-side ({@code PacketCSAdminCommand.processPacketOnServer}) against the
 * server's command registry, so a client-only mod's commands are invisible in multiplayer.
 */
public class AnnotationControls {

	private final StarMod mod;
	private final AnnotationStore store;

	private KeyboardMappings addLabel;
	private KeyboardMappings addLeaderLabel;
	private KeyboardMappings cycleSize;

	/** Size applied to newly created annotations. Adjusted with the cycle-size binding. */
	private LabelSize currentSize = LabelSize.MEDIUM;

	public AnnotationControls(StarMod mod, AnnotationStore store) {
		this.mod = mod;
		this.store = store;
	}

	public void register() {
		addLabel = KeyboardMappings.registerMapping(mod,
				"BetterBuilding: Add label", Keyboard.KEY_N, KeyboardContext.BUILD);
		addLeaderLabel = KeyboardMappings.registerMapping(mod,
				"BetterBuilding: Add leader label", Keyboard.KEY_M, KeyboardContext.BUILD);
		cycleSize = KeyboardMappings.registerMapping(mod,
				"BetterBuilding: Cycle label size", Keyboard.KEY_B, KeyboardContext.BUILD);

		StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				//key-down only; the event fires for release too
				if(!event.isKeyDown()) {
					return;
				}
				if(event.isMapping(addLabel)) {
					promptForLabel(false);
				} else if(event.isMapping(addLeaderLabel)) {
					promptForLabel(true);
				} else if(event.isMapping(cycleSize)) {
					cycleSize();
				}
			}
		}, mod);
	}

	public LabelSize getCurrentSize() {
		return currentSize;
	}

	private void cycleSize() {
		LabelSize[] all = LabelSize.values();
		currentSize = all[(currentSize.ordinal() + 1) % all.length];
		GameClient.sendMessage("[BetterBuilding] new labels: " + currentSize.getDisplayName());
	}

	/**
	 * Captures the targeted block first, then opens the text prompt. Doing it in that order
	 * matters: opening the dialog takes the player out of block targeting, so resolving the
	 * anchor afterwards would find nothing selected.
	 */
	private void promptForLabel(final boolean leader) {
		SegmentPiece piece = selectedBlock();
		if(piece == null) {
			GameClient.sendMessage("[BetterBuilding] look at a block in build mode first");
			return;
		}
		SegmentController c = piece.getSegmentController();
		if(c == null || c.getUniqueIdentifier() == null) {
			GameClient.sendMessage("[BetterBuilding] that block's entity is not ready yet");
			return;
		}
		final Anchor anchor = Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(),
				piece.getAbsolutePosX(), piece.getAbsolutePosY(), piece.getAbsolutePosZ());
		anchor.blockIndex = piece.getAbsoluteIndex();

		final LabelSize size = currentSize;
		new SimplePlayerTextInput("Annotation", leader ? "Leader label text" : "Label text") {
			@Override
			public boolean onInput(String text) {
				if(text == null || text.trim().isEmpty()) {
					return false;
				}
				Annotation a = leader
						? Annotation.leaderLabel(anchor, text.trim())
						: Annotation.label(anchor, text.trim());
				a.size = size;
				store.add(a);
				return true;
			}
		};
	}

	private SegmentPiece selectedBlock() {
		GameClientState state = GameClient.getClientState();
		if(state == null) {
			return null;
		}
		try {
			return state.getGlobalGameControlManager()
					.getIngameControlManager()
					.getPlayerGameControlManager()
					.getPlayerIntercationManager()
					.getSelectedBlockByActiveController();
		} catch(Exception e) {
			//the control manager chain is only fully wired while in game
			return null;
		}
	}
}
