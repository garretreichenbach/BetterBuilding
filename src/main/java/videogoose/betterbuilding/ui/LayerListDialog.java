package videogoose.betterbuilding.ui;

import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.input.InputState;

import api.utils.gui.GUIInputDialog;
import api.utils.gui.GUIInputDialogPanel;
import videogoose.betterbuilding.annotation.AnnotationStore;

/** Dialog listing the layers on an entity, with per-layer show/hide. */
public class LayerListDialog extends GUIInputDialog {

	private final AnnotationStore store;
	private final String entityKey;

	public LayerListDialog(AnnotationStore store, String entityKey) {
		super();
		this.store = store;
		this.entityKey = entityKey;
	}

	@Override
	public GUIInputDialogPanel createPanel() {
		return new LayerListPanel(getState(), store, entityKey, this);
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

	/** Window body for the layer list. */
	public static class LayerListPanel extends GUIInputDialogPanel {

		private final AnnotationStore store;
		private final String entityKey;

		public LayerListPanel(InputState state, AnnotationStore store, String entityKey, GUICallback callback) {
			super(state, "bb_layer_list", "Layers", 600, 400, callback);
			this.store = store;
			this.entityKey = entityKey;
		}

		@Override
		public void onInit() {
			super.onInit();
			GUIContentPane content = ((GUIDialogWindow) background).getMainContentPane();
			content.setTextBoxHeightLast(330);

			GUIAncor anchor = content.getContent(0);
			LayerTableList list = new LayerTableList(getState(), 600, 330, anchor, store, entityKey);
			list.onInit();
			anchor.attach(list);
		}
	}
}
