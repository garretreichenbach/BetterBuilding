package net.thederpgamer.betterbuilding.gui;

import api.common.GameClient;
import api.utils.StarRunnable;
import net.thederpgamer.betterbuilding.BetterBuilding;
import net.thederpgamer.betterbuilding.inventory.HotbarData;
import net.thederpgamer.betterbuilding.util.GUIScale;
import net.thederpgamer.betterbuilding.util.HotbarUtils;
import org.schema.game.client.view.gui.inventory.inventorynew.InventoryPanelNew;
import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIInnerBackground;
import org.schema.schine.input.InputState;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;
import javax.vecmath.Vector2f;
import java.io.IOException;

/**
 * BuildHotbar.java
 * Build Hotbar GUI
 * ==================================================
 * Created 01/22/2021
 * @author TheDerpGamer
 */
public class BuildHotbar extends BottomBarBuild {

    //Data
    private Inventory inventory;
    private int activeHotbar;
    private HotbarData[][] hotbars;

    //GUI
    public boolean hideHotbars;
    private GUITextOverlay barIndexText;
    public GUIAncor bgIndexAnchor;
    private GUIInnerBackground bgIndex;
    private GUIOverlay upButton;
    private GUIOverlay downButton;
    private GUITextOverlay posText;

    public BuildHotbar(InputState inputState, InventoryPanelNew inventoryPanel) {
        super(inputState, inventoryPanel);
        inventory = inventoryPanel.getOwnPlayer().getInventory();
        activeHotbar = 0;
        hotbars = HotbarUtils.loadHotbars();
    }

    /**
     * @return True if any number key is currently being pressed
     */
    public boolean anyNumberKeyDown() {
        for(int i = 2; i < 12; i ++) {
            if(Keyboard.isKeyDown(i)) return true;
        }
        return false;
    }

    /**
     * Gets the hotbar number from the specified key
     * If the key was not a number key, returns the current hotbar
     * @param key The key to check
     * @return The hotbar number
     */
    public int getHotbarNumber(int key) {
        if(anyNumberKeyDown()) {
            return key - 2;
        } else {
            return activeHotbar;
        }
    }

    /**
     * Saves the active hotbar to slot
     */
    public void saveActive() {
        for(int i = 0; i < 10; i ++) {
            hotbars[activeHotbar][i] = new HotbarData(inventory.getMap().get(i));
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
        for(int i = 0; i < 10; i ++) {
            InventorySlot newSlot = (getActive()[i] != null) ? getActive()[i].toInventorySlot() : new InventorySlot();
            inventory.getMap().remove(i);
            inventory.getMap().put(i, newSlot);
        }
        barIndexText.setTextSimple(String.valueOf((activeHotbar + 1)));
    }

    /**
     * Cycles the active hotbar to the next slot
     */
    public void cycleNext() {
        saveActive();
        if(activeHotbar == 9) {
            activeHotbar = 0;
        } else {
            activeHotbar ++;
        }
        updateHotbar();
    }

    /**
     * Cycles the active hotbar to the previous slot
     */
    public void cyclePrevious() {
        saveActive();
        if(activeHotbar == 0) {
            activeHotbar = 9;
        } else {
            activeHotbar --;
        }
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

    public void startAutoSaveTimer(int ticks) {
        new StarRunnable() {
            @Override
            public void run() {
                HotbarUtils.saveHotbars(hotbars);
            }
        }.runTimer(BetterBuilding.getInstance(), ticks);
    }

    public void displayPosText() {
        posText.setTextSimple("(" + (int) bgIndexAnchor.getPos().x + ", " + (int) bgIndexAnchor.getPos().y + ")");
        posText.setVisibility(1);
        new StarRunnable() {
            @Override
            public void run() {
                posText.setVisibility(2);
            }
        }.runLater(BetterBuilding.getInstance(), 25 * 5);
    }

    public void setSavedPos(Vector2f pos) throws IOException {
        BetterBuilding.getInstance().hotbarPos = pos;
        BetterBuilding.getInstance().getConfig("config").set("hotbar-pos", (int) pos.x + ", " + (int) pos.y);
        BetterBuilding.getInstance().getConfig("config").saveConfig();
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
                if (event.pressedLeftMouse()) {
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
                if (event.pressedLeftMouse()) {
                    cyclePrevious();
                }
            }
        });

        upButton.onInit();
        downButton.onInit();
        barIndexText.onInit();

        orientate(GUIElement.ORIENTATION_HORIZONTAL_MIDDLE | GUIElement.ORIENTATION_BOTTOM);
        bgIndexAnchor.orientate(GUIElement.ORIENTATION_BOTTOM);
        bgIndexAnchor.getPos().x = BetterBuilding.getInstance().hotbarPos.x;
        bgIndexAnchor.getPos().y = BetterBuilding.getInstance().hotbarPos.y;

        posText = new GUITextOverlay(10, 10, FontLibrary.FontSize.MEDIUM, getState());
        posText.orientate(GUIElement.ORIENTATION_TOP | GUIElement.ORIENTATION_LEFT);
        posText.onInit();
        posText.setVisibility(2);

        getHudHelpManager().addHelper(KeyboardMappings.SWITCH_FIRE_MODE, "[+ Number Key or Scroll] Change Hotbar", HudContextHelperContainer.Hos.LEFT, ContextFilter.IMPORTANT);
        getHudHelpManager().addHelper(KeyboardMappings.SWITCH_FIRE_MODE, "[+ Arrow Key] Move Hotbar by 1 pixel\n[+ LShift (Optional)] Move by 30 pixels", HudContextHelperContainer.Hos.LEFT, ContextFilter.IMPORTANT);
    }

    @Override
    public void draw() {
        super.draw();
        if(!hideHotbars) {
            bgIndexAnchor.draw();
            if(!posText.isInvisible()) {
                posText.draw();
            } else {
                posText.cleanUp();
            }
        } else {
            bgIndexAnchor.cleanUp();
            bgIndex.cleanUp();
            posText.cleanUp();
        }
    }
}
