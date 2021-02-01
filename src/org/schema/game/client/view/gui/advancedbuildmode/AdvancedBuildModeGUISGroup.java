package org.schema.game.client.view.gui.advancedbuildmode;

import javax.vecmath.Vector4f;
import org.schema.game.client.controller.manager.ingame.AbstractSizeSetting;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.SymmetryPlanes;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.game.client.view.gui.advanced.tools.SliderCallback;
import org.schema.game.client.view.gui.advanced.tools.SliderResult;
import org.schema.schine.graphicsengine.forms.gui.newgui.settingsnew.GUIScrollSettingSelector;

/**
 * AdvancedBuildModeGUISGroup.java
 * ==================================================
 * Modified 02/01/2021 by TheDerpGamer
 * @author Schema
 */
public abstract class AdvancedBuildModeGUISGroup extends AdvancedGUIGroup {

    public AdvancedBuildModeGUISGroup(AdvancedGUIElement var1) {
        super(var1);
    }

    public BuildToolsManager getBuildToolsManager() {
        return this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
    }

    public PlayerInteractionControlManager getPlayerInteractionControlManager() {
        return this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
    }

    public SymmetryPlanes getSymmetryPlanes() {
        return null;
    }

    public GameClientState getState() {
        return (GameClientState)super.getState();
    }

    public void setInitialBackgroundColor(Vector4f var1) {
        var1.set(1.0F, 1.0F, 1.0F, 0.65F);
    }

    public int getSubListIndex() {
        return 1;
    }

    public boolean isExpandable() {
        return true;
    }

    public boolean isClosable() {
        return false;
    }

    public abstract class SizeSliderResult extends SliderResult {
        private AbstractSizeSetting size;

        public SizeSliderResult(AbstractSizeSetting var2) {
            this.size = var2;
        }

        public int getDefault() {
            return this.size.setting;
        }

        public void onInitializeScrollSetting(GUIScrollSettingSelector var1) {
            this.size.guiCallBack = var1;
        }

        public int getMax() {
            return this.size.getMax();
        }

        public int getMin() {
            return this.size.getMin();
        }

        public boolean showLabel() {
            return false;
        }

        public String getName() {
            return "SizeLabel";
        }

        public SliderCallback initCallback() {
            return new SliderCallback() {
                public void onValueChanged(int var1) {
                    SizeSliderResult.this.size.set((float)var1);
                }
            };
        }
    }
}