package thederpgamer.betterbuilding.gui;

import api.common.GameClient;
import org.schema.game.client.view.gui.inventory.inventorynew.InventoryPanelNew;
import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIInnerBackground;
import org.schema.schine.input.InputState;
import org.schema.schine.input.Keyboard;
import thederpgamer.betterbuilding.BetterBuilding;
import thederpgamer.betterbuilding.data.HotbarData;
import thederpgamer.betterbuilding.manager.HotbarManager;

/**
 * Build hotbar GUI element.
 *
 * @version 1.0 - [01/22/2021]
 * @author TheDerpGamer
 */
public class BuildHotbar extends BottomBarBuild {

	//Data
	private int activeHotbar;
	private HotbarData[][] hotbars;

	//GUI
	public boolean hideHotbars;
	private GUITextOverlay barIndexText;
	public GUIAncor bgIndexAnchor;
	private GUIInnerBackground bgIndex;
	private GUIOverlay upButton;
	private GUIOverlay downButton;

	public BuildHotbar(InputState inputState, InventoryPanelNew inventoryPanel) {
		super(inputState, inventoryPanel);
		activeHotbar = 0;
	}

	public Inventory getInventory() {
		return GameClient.getClientPlayerState().getInventory();
	}

	/**
	 * @return True if any number key is currently being pressed
	 */
	public boolean anyNumberKeyDown() {
		for(int i = 2; i < 12; i++) if(Keyboard.isKeyDown(i) && !Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_GRAVE)) return true;
		return false;
	}

	/**
	 * Gets the hotbar number from the specified key
	 * If the key was not a number key, returns the current hotbar
	 * @param key The key to check
	 * @return The hotbar number
	 */
	public int getHotbarNumber(int key) {
		if(anyNumberKeyDown()) return key - 2;
		else return activeHotbar;
	}

	/**
	 * Saves the active hotbar to slot
	 */
	public void saveActive() {
		Inventory inventory = getInventory();
		if(inventory != null && inventory.getMap() != null && inventory.isInfinite()) {
			for(int i = 0; i < 10; i++) {
				InventorySlot slot = inventory.getMap().get(i);
				if(slot != null && !slot.isMetaItem()) hotbars[activeHotbar][i] = new HotbarData(slot);
			}
		}
	}

	/**
	 * @return The active hotbar elements
	 */
	public HotbarData[] getActive() {
		return hotbars[activeHotbar];
	}

	/**
	 * Updates the active hotbar
	 */
	public void updateHotbar() {
		if(getInventory().isInfinite()) {
			for(int i = 0; i < 10; i++) {
				getInventory().getMap().remove(i);
				if(getActive()[i] != null) {
					try {
						if(ElementKeyMap.isValidType(getActive()[i].type) || getActive()[i].type < 0) { //Only add valid ids or multi slots
							InventorySlot newSlot = (getActive()[i] != null) ? getActive()[i].convertToSlot() : new InventorySlot();
							getInventory().getMap().put(i, newSlot);
						}
					} catch(NullPointerException exception) {
						BetterBuilding.getInstance().logException("Failed to update hotbar slot " + i + " with type " + getActive()[i].type, exception);
					}
				}
			}
			barIndexText.setTextSimple(String.valueOf((activeHotbar + 1)));
		} else hideHotbars = true;
	}

	/**
	 * Cycles the active hotbar to the next slot
	 */
	public void cycleNext() {
		saveActive();
		if(activeHotbar == 9) activeHotbar = 0;
		else activeHotbar++;
		updateHotbar();
	}

	/**
	 * Cycles the active hotbar to the previous slot
	 */
	public void cyclePrevious() {
		saveActive();
		if(activeHotbar == 0) activeHotbar = 9;
		else activeHotbar--;
		updateHotbar();
	}

	/**
	 * Sets the active hotbar to a specified slot
	 */
	public void setActiveHotbar(int slot) {
		saveActive();
		activeHotbar = slot;
		updateHotbar();
	}

	private HudContextHelpManager getHudHelpManager() {
		return GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getHelpManager();
	}

	@Override
	public void onInit() {
		super.onInit();

		barIndexText = new GUITextOverlay(10, 10, FontLibrary.FontSize.BIG, getState());
		barIndexText.setTextSimple(String.valueOf((activeHotbar + 1)));
		barIndexText.setPos(GUIScale.S.defaultInset, GUIScale.S.scale(28), 0);

		bgIndexAnchor = new GUIAncor(getState(), GUIScale.S.scale(18) + (((int) Math.log10(activeHotbar + 1)) * GUIScale.S.scale(11)), GUIScale.S.scale(77));
		bgIndex = new GUIInnerBackground(getState(), bgIndexAnchor, 0);
		bgIndexAnchor.attach(bgIndex);
		bgIndex.attach(barIndexText);
		String p = getState().getGUIPath();
		upButton = new GUIOverlay(Controller.getResLoader().getSprite(p + "UI 16px-8x8-gui-"), getState());
		downButton = new GUIOverlay(Controller.getResLoader().getSprite(p + "UI 16px-8x8-gui-"), getState());

		upButton.setSpriteSubIndex(4);
		downButton.setSpriteSubIndex(5);
		bgIndex.attach(upButton);
		bgIndex.attach(downButton);
		attach(bgIndexAnchor);

		upButton.setPos(GUIScale.S.scale(1), 0, 0);
		downButton.setPos(GUIScale.S.scale(1), bgIndexAnchor.getHeight() - downButton.getHeight(), 0);
		upButton.setMouseUpdateEnabled(true);
		downButton.setMouseUpdateEnabled(true);

		upButton.setCallback(new GUICallback() {

			@Override
			public void callback(GUIElement callingGuiElement, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					cycleNext();
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		});

		downButton.setCallback(new GUICallback() {

			@Override
			public boolean isOccluded() {
				return false;
			}

			@Override
			public void callback(GUIElement callingGuiElement, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					cyclePrevious();
				}
			}
		});

		upButton.onInit();
		downButton.onInit();
		barIndexText.onInit();

		orientate(ORIENTATION_HORIZONTAL_MIDDLE | ORIENTATION_BOTTOM);
		bgIndexAnchor.setPos(GLFrame.getWidth() - 605, GLFrame.getHeight() - 78, 0);
//		getHudHelpManager().addHelper(KeyboardMappings.SWITCH_FIRE_MODE, "[+ Number Key or Scroll] Change Build Hotbar", HudContextHelperContainer.Hos.LEFT, ContextFilter.IMPORTANT);
		hotbars = HotbarManager.loadHotbars();
		updateHotbar();
	}

	@Override
	public void draw() {
		super.draw();
		if(!hideHotbars) {
			bgIndexAnchor.setPos(GLFrame.getWidth() - 605, GLFrame.getHeight() - 78, 0);
			bgIndexAnchor.draw();
		} else {
			bgIndexAnchor.cleanUp();
			bgIndex.cleanUp();
		}
	}
}
