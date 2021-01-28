package net.thederpgamer.betterbuilding.gui.advancedbuildmode;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.NewAdvancedBuildModeSymmetry;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.game.client.view.gui.advancedbuildmode.*;
import java.util.List;

/**
 * NewAdvancedBuildMode.java
 * Improved version of AdvancedBuildMode
 * ==================================================
 * Created 01/27/2021
 * @author TheDerpGamer
 */
public class NewAdvancedBuildMode extends AdvancedBuildMode {

    public NewAdvancedBuildMode(GameClientState state) {
        super(state);
    }

    @Override
    protected void addGroups(List<AdvancedGUIGroup> groups) {
        groups.add(new AdvancedBuildModeHelpTop(this));
        groups.add(new AdvancedBuildModeBlockPreview(this));
        groups.add(new AdvancedBuildModeBrushSize(this));
        groups.add(new NewAdvancedBuildModeSymmetry(this));
        groups.add(new AdvancedBuildModeSelection(this));
        groups.add(new AdvancedBuildModeFill(this));
        groups.add(new AdvancedBuildModeShape(this));
        groups.add(new AdvancedBuildModeDocking(this));
        groups.add(new AdvancedBuildModeReactor(this));
        groups.add(new AdvancedBuildModeDisplay(this));
        groups.add(new AdvancedBuildModeHotbar(this));
        groups.add(new AdvancedBuildModeHelp(this));
    }
}
