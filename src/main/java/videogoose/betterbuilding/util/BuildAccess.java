package videogoose.betterbuilding.util;

import api.common.GameClient;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.common.controller.EditableSendableSegmentController;

/**
 * Convenience accessors for the local client's build-mode state.
 */
public final class BuildAccess {

	private BuildAccess() {}

	/** @return the client's build tools manager, or {@code null} if unavailable. */
	public static BuildToolsManager buildTools() {
		try {
			return GameClient.getClientState()
					.getGlobalGameControlManager()
					.getIngameControlManager()
					.getPlayerGameControlManager()
					.getPlayerIntercationManager()
					.getBuildToolsManager();
		} catch (Exception e) {
			return null;
		}
	}

	/** @return the entity the player is currently building on, or {@code null}. */
	public static EditableSendableSegmentController controlledEntity() {
		try {
			return GameClient.getPICM().getSegmentControlManager().getSegmentController();
		} catch (Exception e) {
			return null;
		}
	}
}
