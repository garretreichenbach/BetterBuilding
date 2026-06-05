package videogoose.betterbuilding.gui;

import api.common.GameClient;
import api.utils.game.PlayerUtils;
import api.utils.gui.GUIInputDialogPanel;
import api.utils.gui.SimpleGUIVerticalButtonPane;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUITextButton;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.input.InputState;
import videogoose.betterbuilding.content.PaletteWriter;
import videogoose.betterbuilding.slot.ShapeInference;
import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.util.BbFiles;

import java.io.File;
import java.util.Map;

/**
 * The palette editor panel: one "Assign &lt;SLOT&gt;" button per material slot,
 * plus Save/Cancel, and a live status overlay listing the current mapping.
 *
 * <p>Assigning reads the block currently selected in the player's hotbar
 * ({@code getSelectedTypeWithSub}) and records its base block for the slot —
 * non-destructive, it only copies the type id.
 */
public class PaletteEditorPanel extends GUIInputDialogPanel {

	private GUITextOverlay status;

	public PaletteEditorPanel(InputState inputState, GUICallback guiCallback) {
		super(inputState, "bb_palette_editor", "Palette Editor",
				"Select a block in your hotbar, then click a slot to assign it. Save when done.",
				460, 480, guiCallback);
	}

	@Override
	public void onInit() {
		super.onInit();
		GUIContentPane content = ((GUIDialogWindow) background).getMainContentPane();
		content.setTextBoxHeightLast(250);
		GUIAncor top = content.getContent(0);

		SimpleGUIVerticalButtonPane pane = new SimpleGUIVerticalButtonPane(getState(), 220, 240);
		PaletteEditorState st = PaletteEditorState.current;
		if (st != null) {
			for (Slot slot : st.draft.keySet()) {
				GUITextButton b = new GUITextButton(getState(), 200, 22, "Assign " + slot.name(), getCallback());
				b.setUserPointer("SLOT:" + slot.name());
				pane.addButton(b);
			}
		}
		GUITextButton save = new GUITextButton(getState(), 200, 22, "SAVE", getCallback());
		save.setUserPointer("SAVE");
		pane.addButton(save);
		GUITextButton cancel = new GUITextButton(getState(), 200, 22, "CANCEL", getCallback());
		cancel.setUserPointer("CANCEL");
		pane.addButton(cancel);
		top.attach(pane);

		GUIAncor lower = content.addNewTextBox(0, 210).getContent();
		status = new GUITextOverlay(10, 10, getState());
		status.autoWrapOn = lower;
		status.onInit();
		status.setTextSimple(renderDraft());
		lower.attach(status);
	}

	/** Assign the player's currently-selected build block to a slot. */
	public void assign(Slot slot) {
		short sel;
		try {
			sel = GameClient.getPICM().getSelectedTypeWithSub();
		} catch (Exception e) {
			sel = 0;
		}
		if (sel <= 0 || !ElementKeyMap.isValidType(sel)) {
			PlayerUtils.sendMessage(GameClient.getClientPlayerState(),
					"[bb] Select a block in your hotbar first, then click the slot.");
			return;
		}
		short base = ShapeInference.baseOf(sel);
		if (PaletteEditorState.current != null) {
			PaletteEditorState.current.draft.put(slot, base);
		}
		if (status != null) status.setTextSimple(renderDraft());
	}

	/** Write the current draft to disk. */
	public void save() {
		PaletteEditorState st = PaletteEditorState.current;
		if (st == null) return;
		File f = new File(BbFiles.palettes(), st.name + ".json");
		try {
			PaletteWriter.write(f, st.name, st.draft);
			PlayerUtils.sendMessage(GameClient.getClientPlayerState(),
					"[bb] Saved palette '" + st.name + "' to " + f.getPath());
		} catch (Exception e) {
			PlayerUtils.sendMessage(GameClient.getClientPlayerState(), "[bb] Save failed: " + e.getMessage());
		}
	}

	private String renderDraft() {
		PaletteEditorState st = PaletteEditorState.current;
		if (st == null) return "";
		StringBuilder sb = new StringBuilder("Palette: ").append(st.name).append("\n\n");
		for (Map.Entry<Slot, Short> e : st.draft.entrySet()) {
			short v = e.getValue();
			sb.append(e.getKey().name()).append(":  ").append(v > 0 ? blockName(v) : "—").append('\n');
		}
		return sb.toString();
	}

	private String blockName(short type) {
		ElementInformation info = ElementKeyMap.getInfoFast(type);
		return info != null ? info.getName() : ("type " + type);
	}
}
