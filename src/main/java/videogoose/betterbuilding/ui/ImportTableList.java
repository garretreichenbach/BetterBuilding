package videogoose.betterbuilding.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUIElementList;
import org.schema.schine.graphicsengine.forms.gui.GUIListElement;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalArea.HButtonColor;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalButtonTablePane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUITextOverlayTable;
import org.schema.schine.graphicsengine.forms.gui.newgui.ScrollableTableList;
import org.schema.schine.input.InputState;

import api.common.GameClient;
import api.utils.other.LangUtil;
import videogoose.betterbuilding.io.AnnotationFile;
import videogoose.betterbuilding.io.AnnotationIO;
import videogoose.betterbuilding.io.ImportMode;

/**
 * Lists exported annotation files, with a merge mode per row.
 * <p>
 * The three modes are offered as separate buttons rather than a mode selector plus one
 * Import button, so the destructive one (Replace) can never be armed by accident from a
 * previous click.
 */
public class ImportTableList extends ScrollableTableList<File> {

	private final AnnotationIO io;
	private final SegmentController entity;
	private final StatusSink status;

	/**
	 * Cached row summaries, keyed by path and modification time. updateListEntries runs on
	 * every list rebuild, and parsing every file each time would put disk reads in the UI
	 * path for no benefit - the contents cannot change unless the file does.
	 */
	private final Map<String, String> descriptions = new HashMap<String, String>();

	/** Lets the hosting dialog surface the import result. */
	public interface StatusSink {
		void setStatus(String message);
	}

	public ImportTableList(InputState state, float width, float height, GUIElement parent,
			AnnotationIO io, SegmentController entity, StatusSink status) {
		super(state, width, height, parent);
		this.io = io;
		this.entity = entity;
		this.status = status;
	}

	public void redrawList() {
		clear();
		handleDirty();
	}

	@Override
	public void initColumns() {
		addColumn("File", 4.0f, new Comparator<File>() {
			@Override
			public int compare(File a, File b) {
				return LangUtil.stringsCompareTo(a.getName(), b.getName());
			}
		});
		addColumn("Contents", 4.0f, new Comparator<File>() {
			@Override
			public int compare(File a, File b) {
				return Long.compare(a.length(), b.length());
			}
		});
		activeSortColumnIndex = 0;
	}

	@Override
	protected Collection<File> getElementList() {
		return io.listFiles();
	}

	@Override
	public void updateListEntries(GUIElementList list, Set<File> set) {
		setColumnHeight(28);
		for(final File f : set) {
			FileRow row = new FileRow(getState(), f, cell(f.getName()), cell(describe(f)));
			row.expanded = new GUIElementList(getState());
			GUIAncor anchor = new GUIAncor(getState(), getWidth() - 49.0f, 28.0f);
			anchor.attach(buttonPane(f, anchor));
			row.expanded.add(new GUIListElement(anchor, getState()));
			row.expanded.attach(anchor);
			row.onInit();
			list.addWithoutUpdate(row);
		}
		list.updateDim();
	}

	private GUIHorizontalButtonTablePane buttonPane(final File f, GUIAncor anchor) {
		GUIHorizontalButtonTablePane pane = new GUIHorizontalButtonTablePane(getState(), 3, 1, anchor);
		pane.onInit();
		addModeButton(pane, 0, f, ImportMode.MERGE, HButtonColor.GREEN);
		addModeButton(pane, 1, f, ImportMode.APPEND, HButtonColor.BLUE);
		addModeButton(pane, 2, f, ImportMode.REPLACE, HButtonColor.RED);
		return pane;
	}

	private void addModeButton(GUIHorizontalButtonTablePane pane, int x, final File f,
			final ImportMode mode, HButtonColor color) {
		pane.addButton(x, 0, mode.getDisplayName().toUpperCase(), color, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					doImport(f, mode);
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);
	}

	private void doImport(File f, ImportMode mode) {
		try {
			AnnotationFile file = io.read(f);
			if(file == null) {
				report("could not read " + f.getName());
				return;
			}
			report(io.apply(file, entity, mode));
		} catch(Exception e) {
			e.printStackTrace();
			report("import failed: " + e.getMessage());
		}
	}

	private void report(String message) {
		if(status != null) {
			status.setStatus(message);
		}
		GameClient.sendMessage("[BetterBuilding] " + message);
	}

	/** Peeks inside so the user can tell files apart without opening them. */
	private String describe(File f) {
		String key = f.getAbsolutePath() + "@" + f.lastModified();
		String cached = descriptions.get(key);
		if(cached != null) {
			return cached;
		}
		String described = readDescription(f);
		descriptions.put(key, described);
		return described;
	}

	private String readDescription(File f) {
		try {
			AnnotationFile file = io.read(f);
			if(file == null || file.annotations == null) {
				return "unreadable";
			}
			StringBuilder b = new StringBuilder();
			b.append(file.annotations.size()).append(" annotations");
			if(file.sourceEntity != null && !file.sourceEntity.isEmpty()) {
				b.append(" from ").append(file.sourceEntity);
			}
			if(file.layers != null && !file.layers.isEmpty()) {
				b.append(" [").append(join(file.layers)).append("]");
			}
			return b.toString();
		} catch(Exception e) {
			return "unreadable";
		}
	}

	private static String join(java.util.List<String> values) {
		StringBuilder b = new StringBuilder();
		for(int i = 0; i < values.size(); i++) {
			if(i > 0) {
				b.append(", ");
			}
			b.append(values.get(i));
		}
		return b.toString();
	}

	private GUITextOverlayTable cell(String value) {
		GUITextOverlayTable t = new GUITextOverlayTable(10, 10, getState());
		t.setTextSimple(value);
		return t;
	}

	public class FileRow extends ScrollableTableList<File>.Row {
		public FileRow(InputState state, File f, GUIElement... elements) {
			super(state, f, elements);
			highlightSelect = true;
			highlightSelectSimple = true;
			setAllwaysOneSelected(true);
		}
	}
}
