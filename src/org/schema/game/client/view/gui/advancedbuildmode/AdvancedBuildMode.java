package org.schema.game.client.view.gui.advancedbuildmode;

import java.io.IOException;
import java.util.List;
import javax.vecmath.Vector2f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.NewAdvancedBuildModeSymmetry;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.game.client.view.gui.advanced.AdvancedGUIMinimizeCallback;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;

/**
 * AdvancedBuildMode.java
 * ==================================================
 * Modified 02/02/2021 by TheDerpGamer
 * @author Schema
 */
public class AdvancedBuildMode extends AdvancedGUIElement {
    public AdvancedBuildMode(GameClientState var1) {
        super(var1);
        AdvancedGUIMinimizeCallback var2 = new AdvancedGUIMinimizeCallback(var1, true) {
            public boolean isActive() {
                return true;
            }

            public void initialMinimized() {
                this.setMinimizedInitial(EngineSettings.ADVBUILDMODE_MINIMIZED.isOn());
            }

            protected int closeLashButtonOffsetX() {
                return 0;
            }

            public void onMinimized(boolean var1) {
                EngineSettings.ADVBUILDMODE_MINIMIZED.setCurrentState(var1);

                try {
                    EngineSettings.write();
                } catch (IOException var2) {
                    var2.printStackTrace();
                }
            }

            protected boolean isCloseLashOnRight() {
                return false;
            }

            public String getMinimizedText() {
                return Lng.ORG_SCHEMA_GAME_CLIENT_VIEW_GUI_ADVANCEDBUILDMODE_ADVANCEDBUILDMODE_0;
            }

            public String getMaximizedText() {
                return Lng.ORG_SCHEMA_GAME_CLIENT_VIEW_GUI_ADVANCEDBUILDMODE_ADVANCEDBUILDMODE_1;
            }
        };
        this.setMinimizeCallback(var2);
    }

    public GameClientState getState() {
        return (GameClientState)super.getState();
    }

    protected Vector2f getInitialPos() {
        return new Vector2f((float)((int)((float)GLFrame.getWidth() - this.getWidth())), 32.0F);
    }

    public void draw() {
        this.setPos((int)((float)GLFrame.getWidth() - this.getWidth()), 32);
        super.draw();
    }

    public boolean isActive() {
        return super.isActive() && this.getState().getPlayerInputs().isEmpty();
    }

    protected int getScrollerHeight() {
        return GLFrame.getHeight() - 128;
    }

    protected int getScrollerWidth() {
        return 320;
    }

    protected void addGroups(List<AdvancedGUIGroup> var1) {
        var1.add(new AdvancedBuildModeHelpTop(this));
        var1.add(new AdvancedBuildModeBlockPreview(this));
        var1.add(new AdvancedBuildModeBrushSize(this));
        var1.add(new NewAdvancedBuildModeSymmetry(this));
        var1.add(new AdvancedBuildModeSelection(this));
        var1.add(new AdvancedBuildModeFill(this));
        var1.add(new AdvancedBuildModeShape(this));
        var1.add(new AdvancedBuildModeDocking(this));
        var1.add(new AdvancedBuildModeReactor(this));
        var1.add(new AdvancedBuildModeDisplay(this));
        var1.add(new AdvancedBuildModeHotbar(this));
        var1.add(new AdvancedBuildModeHelp(this));
    }

    public void update(Timer var1) {
        super.update(var1);
    }

    public boolean isSelected() {
        return false;
    }
}