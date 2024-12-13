package thederpgamer.betterbuilding.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import org.lwjgl.input.Keyboard;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.inventory.VirtualCreativeModeInventory;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFW;
import thederpgamer.betterbuilding.BetterBuilding;
import thederpgamer.betterbuilding.gui.BuildHotbar;

import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Handles event listeners for BetterBuilding.
 */
public class EventManager {

	public static void registerEvents(BetterBuilding betterBuilding) {
		StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
			@Override
			public void onEvent(MousePressEvent event) {
				PlayerState playerState = GameClient.getClientPlayerState();
				if(playerState != null && GameClient.getControlManager().isInAnyBuildMode()) {
					if(BetterBuilding.getInstance().buildHotbar == null) (BetterBuilding.getInstance().buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
					BetterBuilding.getInstance().buildHotbar.hideHotbars = false;
					GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(BetterBuilding.getInstance().buildHotbar);

					if(event.getScrollDirection() != 0 && Keyboard.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
						if(GameClient.getControlManager().getBuildToolsManager().isPasteMode()) {
							Camera camera = Controller.getCamera();
							Vector3f cameraDir = camera.getForward();
							try {
								Field minField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("min");
								Field maxField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("max");
								Field piecesField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("pieces");

								minField.setAccessible(true);
								maxField.setAccessible(true);
								piecesField.setAccessible(true);

								Vector3i min = (Vector3i) minField.get(getBuildToolsManager().getCopyArea());
								Vector3i max = (Vector3i) maxField.get(getBuildToolsManager().getCopyArea());
								Collection<VoidSegmentPiece> pieces = (Collection<VoidSegmentPiece>) piecesField.get(getBuildToolsManager().getCopyArea());

								Vector3f dir = new Vector3f(cameraDir);
								dir.normalize();
								dir.scale(event.getScrollDirection());
								Vector3i axis = new Vector3i();
								if(Math.abs(dir.x) > Math.abs(dir.y) && Math.abs(dir.x) > Math.abs(dir.z)) axis.x = (int) Math.signum(dir.x);
								else if(Math.abs(dir.y) > Math.abs(dir.x) && Math.abs(dir.y) > Math.abs(dir.z)) axis.y = (int) Math.signum(dir.y);
								else axis.z = (int) Math.signum(dir.z);
								int scrollAmount = Keyboard.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) ? 10 : 1;
								axis.scale(scrollAmount);
								min.add(axis.x, axis.y, axis.z);
								max.add(axis.x, axis.y, axis.z);
								minField.set(getBuildToolsManager().getCopyArea(), min);
								maxField.set(getBuildToolsManager().getCopyArea(), max);
								for(VoidSegmentPiece piece : pieces) piece.voidPos.add(axis.x, axis.y, axis.z);
								event.setCanceled(true);
							} catch(Exception exception) {
								exception.printStackTrace();
							}
						} else if(!BetterBuilding.getInstance().buildHotbar.anyNumberKeyDown() && (GameClient.getClientPlayerState().isUseCreativeMode() || GameClient.getClientPlayerState().getInventory() instanceof VirtualCreativeModeInventory)) {
							if(event.getScrollDirection() > 0) BetterBuilding.getInstance().buildHotbar.cycleNext();
							else if(event.getScrollDirection() < 0) BetterBuilding.getInstance().buildHotbar.cyclePrevious();
							event.setCanceled(true);
						}
					}
				} else if(BetterBuilding.getInstance().buildHotbar != null) {
					BetterBuilding.getInstance().buildHotbar.setActiveHotbar(0);
					BetterBuilding.getInstance().buildHotbar.hideHotbars = true;
				}
			}
		}, betterBuilding);

		StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				PlayerState playerState = GameClient.getClientPlayerState();
				if(playerState != null && !GameClient.getControlManager().getState().isInFlightMode() && (playerState.isUseCreativeMode() || playerState.getInventory() instanceof VirtualCreativeModeInventory) || playerState.getInventory().isInfinite()) {
					if(BetterBuilding.getInstance().buildHotbar == null) (BetterBuilding.getInstance().buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
					BetterBuilding.getInstance().buildHotbar.hideHotbars = false;
					GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(BetterBuilding.getInstance().buildHotbar);

					if(Keyboard.isKeyDown(GLFW.GLFW_KEY_LEFT_ALT)) {
						if(isAnyArrowKeyDown() && GameClient.getControlManager().getBuildToolsManager().isPasteMode()) {
							Camera camera = Controller.getCamera();
							try {
								Field minField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("min");
								Field maxField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("max");
								Field piecesField = getBuildToolsManager().getCopyArea().getClass().getDeclaredField("pieces");

								minField.setAccessible(true);
								maxField.setAccessible(true);
								piecesField.setAccessible(true);

								Vector3i min = (Vector3i) minField.get(getBuildToolsManager().getCopyArea());
								Vector3i max = (Vector3i) maxField.get(getBuildToolsManager().getCopyArea());
								Collection<VoidSegmentPiece> pieces = (Collection<VoidSegmentPiece>) piecesField.get(getBuildToolsManager().getCopyArea());

								int moveAmount = Keyboard.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) ? 10 : 1;
								Vector3f axis = new Vector3f();
								switch(event.getKey()) {
									case GLFW.GLFW_KEY_LEFT://Move left
										axis = new Vector3f(moveAmount, 0, 0);
										break;
									case GLFW.GLFW_KEY_RIGHT://Move right
										axis = new Vector3f(-moveAmount, 0, 0);
										break;
									case GLFW.GLFW_KEY_DOWN://Move towards camera
										axis = new Vector3f(0, 0, -moveAmount);
										break;
									case GLFW.GLFW_KEY_UP://Move away from camera
										axis = new Vector3f(0, 0, moveAmount);
										break;
									case GLFW.GLFW_KEY_PAGE_UP://Move up
										axis = new Vector3f(0, moveAmount, 0);
										break;
									case GLFW.GLFW_KEY_PAGE_DOWN://Move down
										axis = new Vector3f(0, -moveAmount, 0);
										break;
								}

								camera.getWorldTransform().basis.transform(axis);
								Vector3i move = new Vector3i();
								if(Math.abs(axis.x) > Math.abs(axis.y) && Math.abs(axis.x) > Math.abs(axis.z)) move.x = Math.round(axis.x);
								else if(Math.abs(axis.y) > Math.abs(axis.x) && Math.abs(axis.y) > Math.abs(axis.z)) move.y = Math.round(axis.y);
								else move.z = Math.round(axis.z);
								min.add(move.x, move.y, move.z);
								max.add(move.x, move.y, move.z);
								minField.set(getBuildToolsManager().getCopyArea(), min);
								maxField.set(getBuildToolsManager().getCopyArea(), max);
								for(VoidSegmentPiece piece : pieces) piece.voidPos.add(move.x, move.y, move.z);
								event.setCanceled(true);
							} catch(Exception exception) {
								exception.printStackTrace();
							}
						} else if(BetterBuilding.getInstance().buildHotbar.anyNumberKeyDown() && !(Keyboard.isKeyDown(29) || Keyboard.isKeyDown(203) || Keyboard.isKeyDown(205) || Keyboard.isKeyDown(200) || Keyboard.isKeyDown(208))) {
							BetterBuilding.getInstance().buildHotbar.setActiveHotbar(BetterBuilding.getInstance().buildHotbar.getHotbarNumber(event.getKey()));
							event.setCanceled(true);
						}
					}
				} else {
					if(BetterBuilding.getInstance().buildHotbar != null) {
						BetterBuilding.getInstance().buildHotbar.setActiveHotbar(0);
						BetterBuilding.getInstance().buildHotbar.hideHotbars = true;
					}
				}
			}

			private boolean isAnyArrowKeyDown() {
				return Keyboard.isKeyDown(GLFW.GLFW_KEY_LEFT) || Keyboard.isKeyDown(GLFW.GLFW_KEY_RIGHT) || Keyboard.isKeyDown(GLFW.GLFW_KEY_DOWN) || Keyboard.isKeyDown(GLFW.GLFW_KEY_UP) || Keyboard.isKeyDown(GLFW.GLFW_KEY_PAGE_UP) || Keyboard.isKeyDown(GLFW.GLFW_KEY_PAGE_DOWN);
			}
		}, betterBuilding);
	}

	private static BuildToolsManager getBuildToolsManager() {
		return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
	}
}
