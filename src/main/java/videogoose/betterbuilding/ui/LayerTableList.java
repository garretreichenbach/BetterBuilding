package videogoose.betterbuilding.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

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

import api.utils.other.LangUtil;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;

/**
 * Lists the layers in use on an entity, with a show/hide toggle and a count for each.
 * <p>
 * Layers have no registry: one exists exactly when an annotation names it. So this lists
 * what is actually in use rather than what has been declared, and a layer disappears from
 * the list when its last annotation is deleted or moved.
 */
public class LayerTableList extends ScrollableTableList<String> {

	private final AnnotationStore store;
	private final String entityKey;

	public LayerTableList(InputState state, float width, float height, GUIElement parent,
			AnnotationStore store, String entityKey) {
		super(state, width, height, parent);
		this.store = store;
		this.entityKey = entityKey;
	}

	public void redrawList() {
		clear();
		handleDirty();
	}

	@Override
	public void initColumns() {
		addColumn("Layer", 4.0f, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return LangUtil.stringsCompareTo(a, b);
			}
		});
		addColumn("Count", 1.5f, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return Integer.compare(store.countInLayer(entityKey, a), store.countInLayer(entityKey, b));
			}
		});
		addColumn("Shown", 1.5f, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return Boolean.compare(store.isLayerHidden(a), store.isLayerHidden(b));
			}
		});
		activeSortColumnIndex = 0;
	}

	@Override
	protected Collection<String> getElementList() {
		return new ArrayList<String>(store.layersFor(entityKey));
	}

	@Override
	public void updateListEntries(GUIElementList list, Set<String> set) {
		setColumnHeight(28);
		for(final String layer : set) {
			GUITextOverlayTable name = cell(layer);
			GUITextOverlayTable count = cell(String.valueOf(store.countInLayer(entityKey, layer)));
			GUITextOverlayTable shown = cell(store.isLayerHidden(layer) ? "hidden" : "shown");

			LayerRow row = new LayerRow(getState(), layer, name, count, shown);
			row.expanded = new GUIElementList(getState());
			GUIAncor anchor = new GUIAncor(getState(), getWidth() - 49.0f, 28.0f);
			anchor.attach(buttonPane(layer, anchor));
			row.expanded.add(new GUIListElement(anchor, getState()));
			row.expanded.attach(anchor);
			row.onInit();
			list.addWithoutUpdate(row);
		}
		list.updateDim();
	}

	private GUIHorizontalButtonTablePane buttonPane(final String layer, GUIAncor anchor) {
		GUIHorizontalButtonTablePane pane = new GUIHorizontalButtonTablePane(getState(), 2, 1, anchor);
		pane.onInit();

		pane.addButton(0, 0, store.isLayerHidden(layer) ? "SHOW LAYER" : "HIDE LAYER",
				HButtonColor.YELLOW, new GUICallback() {
					@Override
					public void callback(GUIElement element, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							store.setLayerHidden(layer, !store.isLayerHidden(layer));
							redrawList();
						}
					}

					@Override
					public boolean isOccluded() {
						return false;
					}
				}, null);

		pane.addButton(1, 0, "SHOW ONLY THIS", HButtonColor.BLUE, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					//isolate: the common case when focusing on one system of a big ship
					for(String other : store.allLayers()) {
						store.setLayerHidden(other, !other.equals(layer));
					}
					redrawList();
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);

		return pane;
	}

	private GUITextOverlayTable cell(String value) {
		GUITextOverlayTable t = new GUITextOverlayTable(10, 10, getState());
		t.setTextSimple(value);
		return t;
	}

	public class LayerRow extends ScrollableTableList<String>.Row {
		public LayerRow(InputState state, String layer, GUIElement... elements) {
			super(state, layer, elements);
			highlightSelect = true;
			highlightSelectSimple = true;
			setAllwaysOneSelected(true);
		}
	}
}
