package thederpgamer.betterbuilding.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.gui.AdvancedBuildModeGUICreateEvent;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import com.bulletphysics.util.ObjectArrayList;
import org.lwjgl.input.Keyboard;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.view.buildhelper.BuildHelper;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.inventory.VirtualCreativeModeInventory;
import org.schema.schine.input.KeyboardMappings;
import thederpgamer.betterbuilding.BetterBuilding;
import thederpgamer.betterbuilding.gui.BuildHotbar;
import thederpgamer.betterbuilding.gui.advancedbuildmode.BBAdvancedBuildModeShape;

import javax.vecmath.Vector2f;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * [Description]
 *
 * @author TheDerpGamer (MrGoose#0027)
 */
public class EventManager {

	public static void registerEvents(BetterBuilding betterBuilding) {
		StarLoader.registerListener(AdvancedBuildModeGUICreateEvent.class, new Listener<AdvancedBuildModeGUICreateEvent>() {
			@Override
			public void onEvent(AdvancedBuildModeGUICreateEvent event) {
				boolean replaced = false;
				int index = -1;
				for(AdvancedGUIGroup guiGroup : event.getGroups()) {
					if(guiGroup instanceof BBAdvancedBuildModeShape) {
						replaced = true;
						break;
					}
					index ++;
				}
				if(!replaced) {
					event.getGroups().remove(index);
					event.getGroups().add(index, new BBAdvancedBuildModeShape(GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().advancedBuildMode));
				}

				try {
					Field field = getBuildToolsManager().getClass().getDeclaredField("buildHelperClasses");
					field.setAccessible(true);
					ObjectArrayList<Class<? extends BuildHelper>> buildHelpers = (ObjectArrayList<Class<? extends BuildHelper>>) field.get(getBuildToolsManager());
					for(int i = 0; i < getBuildToolsManager().getBuildHelperClasses().size(); i ++) {
						if(buildHelpers.get(i).getName().contains("BuildHelperLine")) {
							if(!buildHelpers.get(i).getName().contains("BBBuildHelperLine")) buildHelpers.set(i, (Class<? extends BuildHelper>) Class.forName("thederpgamer.betterbuilding.gui.advancedbuildmode.helpers.BBBuildHelperLine"));
						}
					}
					field.set(getBuildToolsManager(), buildHelpers);
				} catch(NoSuchFieldException | IllegalAccessException exception) {
					exception.printStackTrace();
				} catch(ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		}, betterBuilding);

		StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
			@Override
			public void onEvent(MousePressEvent event) {
				PlayerState playerState = GameClient.getClientPlayerState();
				if(playerState != null && GameClient.getControlManager().isInAnyBuildMode() && (playerState.isCreativeModeEnabled() || playerState.getInventory() instanceof VirtualCreativeModeInventory)) {
					if(BetterBuilding.getInstance().buildHotbar == null) (BetterBuilding.getInstance().buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
					BetterBuilding.getInstance().buildHotbar.hideHotbars = false;
					GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(BetterBuilding.getInstance().buildHotbar);

					if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping()) && event.getScrollDirection() != 0 && !BetterBuilding.getInstance().buildHotbar.anyNumberKeyDown()) {
						if(event.getScrollDirection() > 0) BetterBuilding.getInstance().buildHotbar.cycleNext();
						else if(event.getScrollDirection() < 0) BetterBuilding.getInstance().buildHotbar.cyclePrevious();
						else return;
						event.setCanceled(true);
					}
				} else {
					if(BetterBuilding.getInstance().buildHotbar != null) {
						BetterBuilding.getInstance().buildHotbar.setActiveHotbar(0);
						BetterBuilding.getInstance().buildHotbar.hideHotbars = true;
					}
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

					if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping())) {
						if(BetterBuilding.getInstance().buildHotbar.anyNumberKeyDown() && !(Keyboard.isKeyDown(29) || Keyboard.isKeyDown(203) || Keyboard.isKeyDown(205) || Keyboard.isKeyDown(200) || Keyboard.isKeyDown(208))) {
							BetterBuilding.getInstance().buildHotbar.setActiveHotbar(BetterBuilding.getInstance().buildHotbar.getHotbarNumber(event.getKey()));
						} else if(Keyboard.isKeyDown(203)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
							int offset = 1;
							if(Keyboard.isKeyDown(29)) offset = 30;
							BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x -= offset;
							BetterBuilding.getInstance().buildHotbar.displayPosText();
							try {
								BetterBuilding.getInstance().buildHotbar.setSavedPos(new Vector2f(BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x, BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y));
							} catch(IOException exception) {
								exception.printStackTrace();
							}
						} else if(Keyboard.isKeyDown(205)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
							int offset = 1;
							if(Keyboard.isKeyDown(29)) offset = 30;
							BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x += offset;
							BetterBuilding.getInstance().buildHotbar.displayPosText();
							try {
								BetterBuilding.getInstance().buildHotbar.setSavedPos(new Vector2f(BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x, BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y));
							} catch(IOException exception) {
								exception.printStackTrace();
							}
						} else if(Keyboard.isKeyDown(200)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
							int offset = 1;
							if(Keyboard.isKeyDown(29)) offset = 30;
							BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y -= offset;
							BetterBuilding.getInstance().buildHotbar.displayPosText();
							try {
								BetterBuilding.getInstance().buildHotbar.setSavedPos(new Vector2f(BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x, BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y));
							} catch(IOException exception) {
								exception.printStackTrace();
							}
						} else if(Keyboard.isKeyDown(208)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
							int offset = 1;
							if(Keyboard.isKeyDown(29)) offset = 30;
							BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y += offset;
							BetterBuilding.getInstance().buildHotbar.displayPosText();
							try {
								BetterBuilding.getInstance().buildHotbar.setSavedPos(new Vector2f(BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().x, BetterBuilding.getInstance().buildHotbar.bgIndexAnchor.getPos().y));
							} catch(IOException exception) {
								exception.printStackTrace();
							}
						}
						event.setCanceled(true);
					}
				} else {
					if(BetterBuilding.getInstance().buildHotbar != null) {
						BetterBuilding.getInstance().buildHotbar.setActiveHotbar(0);
						BetterBuilding.getInstance().buildHotbar.hideHotbars = true;
					}
				}
			}
		}, betterBuilding);
	}

	private static BuildToolsManager getBuildToolsManager() {
		return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
	}
}
