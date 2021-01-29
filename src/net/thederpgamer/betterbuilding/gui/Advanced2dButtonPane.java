package net.thederpgamer.betterbuilding.gui;

import org.schema.game.client.view.gui.advanced.tools.ButtonResult;
import org.schema.game.client.view.gui.advanced.tools.GUIAdvButton;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIInnerTextbox;
import java.util.Arrays;

/**
 * Advanced2dButtonPane.java
 * 2d AdvancedButton Pane
 * ==================================================
 * Created 01/28/2021
 * @author TheDerpGamer
 */
public class Advanced2dButtonPane {

    private GUIInnerTextbox textbox;
    private int xSize;
    private int ySize;
    private GUIAdvButton[][] buttons;

    public Advanced2dButtonPane(GUIInnerTextbox textbox, int xSize, int ySize) {
        this.textbox = textbox;
        this.xSize = xSize;
        this.ySize = ySize;
        this.buttons = new GUIAdvButton[ySize][xSize];
    }

    public GUIInnerTextbox getTextbox() {
        return textbox;
    }

    public int getXSize() {
        return xSize;
    }

    public int getYSize() {
        return ySize;
    }

    public void expandX() {
        xSize ++;
        for(int y = 0; y < ySize; y ++) {
            buttons[y] = Arrays.copyOf(buttons[y], xSize);
        }
    }

    public void expandY() {
        ySize ++;
        buttons = Arrays.copyOf(buttons, ySize);
    }

    public void addButton(int xPos, int yPos, ButtonResult buttonResult) {
        if(yPos >= ySize) while(yPos >= ySize) expandY();
        if(xPos >= xSize) while(xPos >= xSize) expandX();

        GUIAdvButton button = new GUIAdvButton(textbox.getState(), textbox, buttonResult);
        buttons[yPos][xPos] = button;
        textbox.attach(button);
    }

    public void remove() {
        textbox.detachAll();
        textbox.setVisibility(2);
        textbox.cleanUp();
    }

    public GUIAdvButton[][] getButtons() {
        return buttons;
    }

    public void onInit() {
        for(int y = 0; y < ySize; y ++) {
            for(int x = 0; x < xSize; x ++) {
                if(buttons[y][x] != null) {
                    GUIAdvButton button = buttons[y][x];
                    button.onInit();
                    button.setWidth(getButtonWidth());
                    button.setPos((button.getWidth() * x) + 2, (button.getHeight() * y) + 2, 0);
                    button.setInside(true);
                    button.adaptWidth = true;
                    button.adaptHeight = true;
                    textbox.attach(button);
                }
            }
        }
    }

    private float getButtonWidth() {
        return (textbox.getWidth() / xSize) - 2;
    }
}
