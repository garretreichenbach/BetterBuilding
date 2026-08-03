package videogoose.betterbuilding;

import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.gui.ChatMessageParseEvent;
import api.listener.events.gui.HudCreateEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.annotation.Anchor;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;
import videogoose.betterbuilding.render.AnnotationGeometryDrawer;
import videogoose.betterbuilding.render.AnnotationLabelOverlay;
import videogoose.betterbuilding.render.EntityResolver;

/**
 * Building utilities for StarMade, centred on in-world annotations: labels, leader lines
 * and measurements that make a ship document itself.
 * <p>
 * Client-only. Annotations we author live in local storage; annotations already baked into
 * an entity's per-block JSON metadata are read-only from here, since there is no
 * client-to-server API for writing it.
 */
public class BetterBuilding extends StarMod {

	private static BetterBuilding instance;

	private AnnotationStore store;
	private EntityResolver entities;

	public BetterBuilding() {
	}

	public static BetterBuilding getInstance() {
		return instance;
	}

	public AnnotationStore getStore() {
		return store;
	}

	public EntityResolver getEntities() {
		return entities;
	}

	public static void main(String[] args) {
	}

	@Override
	public void onEnable() {
		instance = this;

		store = new AnnotationStore(this);
		store.load();
		entities = new EntityResolver();

		registerWorldDrawer();
		registerHudOverlay();
		registerDebugCommands();
	}

	/** Geometry (leader lines, dimension lines) is drawn in the world pass. */
	private void registerWorldDrawer() {
		StarLoader.registerListener(RegisterWorldDrawersEvent.class, new Listener<RegisterWorldDrawersEvent>() {
			@Override
			public void onEvent(RegisterWorldDrawersEvent event) {
				event.getModDrawables().add(new AnnotationGeometryDrawer(store, entities));
			}
		}, this);
	}

	/** Text is a 2D overlay, so it is drawn in the GUI pass instead. */
	private void registerHudOverlay() {
		StarLoader.registerListener(HudCreateEvent.class, new Listener<HudCreateEvent>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				event.addElement(new AnnotationLabelOverlay(event.getInputState(), store, entities));
			}
		}, this);
	}

	/**
	 * Temporary commands for validating the render path end to end. Scaffolding for the
	 * real build-mode creation flow, not the intended interface.
	 */
	private void registerDebugCommands() {
		StarLoader.registerListener(ChatMessageParseEvent.class, new Listener<ChatMessageParseEvent>() {
			@Override
			public void onEvent(ChatMessageParseEvent event) {
				if(event.getChatMessage() == null) {
					return;
				}
				String message = event.getChatMessage().text;
				if(message == null || !message.startsWith(COMMAND_PREFIX)) {
					return;
				}
				event.setCanceled(true);
				handleCommand(message.substring(COMMAND_PREFIX.length()).trim());
			}
		}, this);
	}

	private static final String COMMAND_PREFIX = "!bb ";

	private void handleCommand(String args) {
		if(args.startsWith("label ")) {
			addLabelAtSelectedBlock(args.substring("label ".length()).trim(), false);
		} else if(args.startsWith("leader ")) {
			addLabelAtSelectedBlock(args.substring("leader ".length()).trim(), true);
		} else if(args.equals("count")) {
			GameClient.sendMessage("[BetterBuilding] " + store.size() + " annotation(s) stored");
		} else {
			GameClient.sendMessage("[BetterBuilding] usage: !bb label <text> | !bb leader <text> | !bb count");
		}
	}

	/**
	 * Anchors an annotation to the block the player currently has selected, which means
	 * being in build mode with a block targeted.
	 */
	private void addLabelAtSelectedBlock(String text, boolean leader) {
		if(text.isEmpty()) {
			GameClient.sendMessage("[BetterBuilding] need some text");
			return;
		}
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
		Anchor anchor = Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(),
				piece.getAbsolutePosX(), piece.getAbsolutePosY(), piece.getAbsolutePosZ());
		anchor.blockIndex = piece.getAbsoluteIndex();

		store.add(leader ? Annotation.leaderLabel(anchor, text) : Annotation.label(anchor, text));
		GameClient.sendMessage("[BetterBuilding] added: " + text);
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
