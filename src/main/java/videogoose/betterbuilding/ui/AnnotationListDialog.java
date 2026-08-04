package videogoose.betterbuilding.ui;

import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;

import api.utils.gui.GUIInputDialog;
import videogoose.betterbuilding.annotation.AnnotationStore;

/** Dialog wrapper for {@link AnnotationListPanel}. */
public class AnnotationListDialog extends GUIInputDialog {

	private final AnnotationStore store;
	private final String entityKey;

	public AnnotationListDialog(AnnotationStore store, String entityKey) {
		super();
		this.store = store;
		this.entityKey = entityKey;
	}

	@Override
	public AnnotationListPanel createPanel() {
		return new AnnotationListPanel(getState(), store, entityKey, this);
	}

	@Override
	public void callback(GUIElement callingElement, MouseEvent mouseEvent) {
		if(isOccluded() || !mouseEvent.pressedLeftMouse()) {
			return;
		}
		Object pointer = callingElement.getUserPointer();
		if(pointer instanceof String) {
			String action = (String) pointer;
			if("X".equals(action) || "CANCEL".equals(action) || "OK".equals(action)) {
				deactivate();
			}
		}
	}
}
