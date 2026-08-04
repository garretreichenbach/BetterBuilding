package videogoose.betterbuilding.ui;

import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.input.InputState;

import api.utils.gui.GUIInputDialog;
import api.utils.gui.GUIInputDialogPanel;
import videogoose.betterbuilding.io.AnnotationIO;

/** Dialog listing exported annotation files, with a merge mode per file. */
public class ImportDialog extends GUIInputDialog {

	private final AnnotationIO io;
	private final SegmentController entity;

	public ImportDialog(AnnotationIO io, SegmentController entity) {
		super();
		this.io = io;
		this.entity = entity;
	}

	@Override
	public GUIInputDialogPanel createPanel() {
		return new ImportPanel(getState(), io, entity, this);
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

	/** Window body: the file list, with a status line underneath. */
	public static class ImportPanel extends GUIInputDialogPanel implements ImportTableList.StatusSink {

		private final AnnotationIO io;
		private final SegmentController entity;

		private String status = "";
		private ImportTableList list;

		public ImportPanel(InputState state, AnnotationIO io, SegmentController entity, GUICallback callback) {
			super(state, "bb_import", "Import annotations", 800, 500, callback);
			this.io = io;
			this.entity = entity;
		}

		@Override
		public void onInit() {
			super.onInit();
			GUIContentPane content = ((GUIDialogWindow) background).getMainContentPane();
			content.setTextBoxHeightLast(390);

			GUIAncor listAnchor = content.getContent(0);
			list = new ImportTableList(getState(), 800, 390, listAnchor, io, entity, this);
			list.onInit();
			listAnchor.attach(list);

			GUIAncor statusAnchor = content.addNewTextBox(0, 40).getContent();
			GUITextOverlay statusText = new GUITextOverlay(10, 10, getState()) {
				@Override
				public void draw() {
					setTextSimple(status.isEmpty()
							? "Expand a file to choose how to import it"
							: status);
					super.draw();
				}
			};
			statusText.autoWrapOn = statusAnchor;
			statusText.onInit();
			statusAnchor.attach(statusText);
		}

		@Override
		public void setStatus(String message) {
			status = message;
			if(list != null) {
				list.redrawList();
			}
		}
	}
}
