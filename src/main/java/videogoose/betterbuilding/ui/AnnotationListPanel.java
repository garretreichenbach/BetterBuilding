package videogoose.betterbuilding.ui;

import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.input.InputState;

import api.utils.gui.GUIInputDialogPanel;
import videogoose.betterbuilding.annotation.AnnotationStore;

/** Window body for the annotation list. */
public class AnnotationListPanel extends GUIInputDialogPanel {

	private final AnnotationStore store;
	private final String entityKey;

	private AnnotationTableList list;

	public AnnotationListPanel(InputState state, AnnotationStore store, String entityKey, GUICallback callback) {
		super(state, "bb_annotation_list", "Annotations", 800, 500, callback);
		this.store = store;
		this.entityKey = entityKey;
	}

	@Override
	public void onInit() {
		super.onInit();
		GUIContentPane content = ((GUIDialogWindow) background).getMainContentPane();
		content.setTextBoxHeightLast(430);

		GUIAncor anchor = content.getContent(0);
		list = new AnnotationTableList(getState(), 800, 430, anchor, store, entityKey);
		list.onInit();
		anchor.attach(list);
	}

	public AnnotationTableList getList() {
		return list;
	}
}
