package videogoose.betterbuilding.gui;

import api.utils.gui.GUIInputDialog;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import videogoose.betterbuilding.slot.SlotRegistry;

/**
 * The palette editor dialog. Routes button clicks (by their string user-pointer)
 * to the {@link PaletteEditorPanel}: assign a slot, save, or cancel.
 */
public class PaletteEditorDialog extends GUIInputDialog {

	public PaletteEditorDialog() {
		super();
	}

	@Override
	public PaletteEditorPanel createPanel() {
		return new PaletteEditorPanel(getState(), this);
	}

	@Override
	public void callback(GUIElement callingElement, MouseEvent mouseEvent) {
		if (isOccluded() || !mouseEvent.pressedLeftMouse()) return;
		Object up = callingElement.getUserPointer();
		if (!(up instanceof String)) return;
		String tag = (String) up;
		PaletteEditorPanel panel = (PaletteEditorPanel) getInputPanel();

		if ("SAVE".equals(tag)) {
			panel.save();
			deactivate();
		} else if ("CANCEL".equals(tag) || "X".equals(tag)) {
			deactivate();
		} else if (tag.startsWith("SLOT:")) {
			panel.assign(SlotRegistry.getOrCreate(tag.substring("SLOT:".length())));
		}
	}
}
