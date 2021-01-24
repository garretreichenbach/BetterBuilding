package net.thederpgamer.betterbuilding.gui;

import api.utils.StarRunnable;
import net.thederpgamer.betterbuilding.BetterBuilding;
import net.thederpgamer.betterbuilding.util.GUIScale;
import org.schema.game.client.view.gui.inventory.inventorynew.InventoryPanelNew;
import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIInnerBackground;
import org.schema.schine.input.InputState;
import org.schema.schine.input.Keyboard;
import javax.vecmath.Vector2f;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * BuildHotbar.java
 * Build Hotbar GUI
 * ==================================================
 * Created 01/22/2021
 * @author TheDerpGamer
 */
public class BuildHotbar extends BottomBarBuild {

    private Inventory inventory;
    private int activeHotbar;
    private final short[][] hotbars;

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
        hotbars = new short[10][10];
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
        short[] elements = new short[10];
        for(int i = 0; i < 10; i ++) {
            try {
                elements[i] = inventory.getSlot(i).getType();
            } catch (Exception ignored) { }
        }
        hotbars[activeHotbar] = elements;
    }

    /**
     * @return The active hotbar elements
     */
    public short[] getActive() {
        return hotbars[activeHotbar];
    }

    /**
     * Updates the active hotbar
     */
    public void updateHotbar() {
        for(int i = 0; i < 10; i ++) {
            try {
                inventory.getSlot(i).clear();
                inventory.setSlot(i, getActive()[i], 1, -1);
                inventory.getSlot(i).setInfinite(true);
            } catch (Exception ignored) { }
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

    public void displayPosText() {
        posText.setTextSimple("(" + bgIndexAnchor.getPos().x + ", " + bgIndexAnchor.getPos().y + ")");
        posText.setVisibility(1);
        posText.draw();
        new StarRunnable(){
            @Override
            public void run() {
                posText.setVisibility(2);
                posText.cleanUp();
            }
        }.runLater(BetterBuilding.getInstance(), 25 * 5);
    }

    public void setSavedPos(Vector2f pos) throws IOException {
        File posFile = new File(BetterBuilding.getInstance().getResourcesFolder().getPath() + "/savedPos.smdat");
        if(posFile.exists()) posFile.delete();
        posFile.createNewFile();
        FileWriter writer = new FileWriter(posFile);
        writer.write(pos.toString());
        writer.close();
    }

    public Vector2f getSavedPos() throws IOException, NumberFormatException {
        File posFile = new File(BetterBuilding.getInstance().getResourcesFolder().getPath() + "/savedPos.smdat");
        if(!posFile.exists()) {
            posFile.createNewFile();
            FileWriter writer = new FileWriter(posFile);
            writer.write(new Vector2f(getWidth() + 14, 1).toString());
            writer.close();
        }
        Scanner scanner = new Scanner(posFile);
        String line = scanner.nextLine();
        scanner.close();
        String[] coords = line.split(", ");
        float x = Float.parseFloat(String.valueOf(coords[0].toCharArray()[1]));
        float y = Float.parseFloat(String.valueOf(coords[1].toCharArray()[1]));
        return new Vector2f(x, y);
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
        doOrientation();
    }

    @Override
    public void doOrientation() {
        super.doOrientation();
        try {
            orientate(GUIElement.ORIENTATION_HORIZONTAL_MIDDLE | GUIElement.ORIENTATION_BOTTOM);
            bgIndexAnchor.orientate(GUIElement.ORIENTATION_BOTTOM);
            bgIndexAnchor.getPos().x = getSavedPos().x;
            bgIndexAnchor.getPos().y = getSavedPos().y;

            posText = new GUITextOverlay(10, 10, FontLibrary.FontSize.SMALL, getState());
            posText.setPos(bgIndex.getPos().x + 15, bgIndexAnchor.getPos().y, 0);
            posText.onInit();
            posText.setVisibility(2);

        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void draw() {
        super.draw();
        if(!hideHotbars) {
            bgIndexAnchor.draw();
        } else {
            bgIndexAnchor.cleanUp();
            bgIndex.cleanUp();
        }
    }
}
