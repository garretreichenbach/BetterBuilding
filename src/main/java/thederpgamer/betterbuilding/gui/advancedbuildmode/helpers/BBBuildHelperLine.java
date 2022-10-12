package thederpgamer.betterbuilding.gui.advancedbuildmode.helpers;

import org.schema.game.client.view.buildhelper.BuildHelperLine;
import org.schema.schine.common.InputHandler;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.Transformable;
import org.schema.schine.input.KeyEventInterface;
import thederpgamer.betterbuilding.gui.advancedbuildmode.BBAdvancedBuildModeShape;

/**
 * [Description]
 *
 * @author TheDerpGamer (MrGoose#0027)
 */
public class BBBuildHelperLine extends BuildHelperLine implements InputHandler {


	public BBBuildHelperLine(Transformable transformable) {
		super(transformable);
	}

	@Override
	public void handleKeyEvent(KeyEventInterface keyEventInterface) {

	}

	@Override
	public void handleMouseEvent(MouseEvent mouseEvent) {
		if(BBAdvancedBuildModeShape.autoBuildEnabled) {
			for(Long pose : getPoses()) {
				try {
					if(mouseEvent.pressedSecondary()) BBAdvancedBuildModeShape.buildBlock(pose);
					else if(mouseEvent.pressedPrimary()) BBAdvancedBuildModeShape.removeBlock(pose);
				} catch(Exception exception) {
					exception.printStackTrace();
				}
			}
		}
	}
}
