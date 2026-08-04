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
import org.schema.schine.graphicsengine.forms.gui.newgui.ControllerElement.FilterRowStyle;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalArea.HButtonColor;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalButtonTablePane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIListFilterText;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUITextOverlayTable;
import org.schema.schine.graphicsengine.forms.gui.newgui.ScrollableTableList;
import org.schema.schine.input.InputState;

import api.utils.gui.SimplePlayerTextInput;
import api.utils.other.LangUtil;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationType;
import videogoose.betterbuilding.annotation.AnnotationStore;
import videogoose.betterbuilding.annotation.LabelSize;
import videogoose.betterbuilding.render.AnnotationGeometry;

/**
 * Lists every annotation on one entity, with per-row actions.
 * <p>
 * Each row expands to reveal edit, size, hide, locate and delete. That is the whole point
 * of the list: without a way to address a specific annotation, the panel could only ever
 * offer blunt operations like "remove the last one".
 */
public class AnnotationTableList extends ScrollableTableList<Annotation> {

	private final AnnotationStore store;

	/** Entity whose annotations are listed. Null means nothing to show. */
	private String entityKey;

	public AnnotationTableList(InputState state, float width, float height, GUIElement parent,
			AnnotationStore store, String entityKey) {
		super(state, width, height, parent);
		this.store = store;
		this.entityKey = entityKey;
	}

	public void setEntityKey(String entityKey) {
		this.entityKey = entityKey;
	}

	public void redrawList() {
		clear();
		handleDirty();
	}

	@Override
	public void initColumns() {
		addColumn("Type", 1.5f, new Comparator<Annotation>() {
			@Override
			public int compare(Annotation a, Annotation b) {
				return a.type.name().compareTo(b.type.name());
			}
		});
		addColumn("Text", 5.0f, new Comparator<Annotation>() {
			@Override
			public int compare(Annotation a, Annotation b) {
				return LangUtil.stringsCompareTo(safe(a.text), safe(b.text));
			}
		});
		addColumn("Layer", 2.0f, new Comparator<Annotation>() {
			@Override
			public int compare(Annotation a, Annotation b) {
				return LangUtil.stringsCompareTo(safe(a.layer), safe(b.layer));
			}
		});
		addColumn("Position", 2.5f, new Comparator<Annotation>() {
			@Override
			public int compare(Annotation a, Annotation b) {
				return LangUtil.stringsCompareTo(position(a), position(b));
			}
		});

		addTextFilter(new GUIListFilterText<Annotation>() {
			@Override
			public boolean isOk(String s, Annotation a) {
				return LangUtil.stringsContain(safe(a.text), s) || LangUtil.stringsContain(safe(a.layer), s);
			}
		}, "Search text or layer", FilterRowStyle.FULL);

		activeSortColumnIndex = 0;
	}

	@Override
	protected Collection<Annotation> getElementList() {
		if(entityKey == null) {
			return new ArrayList<Annotation>();
		}
		//copied rather than returned live: rows are built while iterating, and a delete
		//from a row action would otherwise mutate the list mid-iteration
		return new ArrayList<Annotation>(store.forEntity(entityKey));
	}

	@Override
	public void updateListEntries(GUIElementList list, Set<Annotation> set) {
		setColumnHeight(28);
		for(final Annotation a : set) {
			GUITextOverlayTable type = cell(label(a.type.name()));
			GUITextOverlayTable text = cell(describe(a));
			//mark layer-hidden rows, otherwise an annotation that is not drawn looks
			//identical here to one that is
			GUITextOverlayTable layer = cell(store.isLayerHidden(a.layer)
					? safe(a.layer) + " (hidden)"
					: safe(a.layer));
			GUITextOverlayTable pos = cell(position(a));

			AnnotationRow row = new AnnotationRow(getState(), a, type, text, layer, pos);
			row.expanded = new GUIElementList(getState());
			GUIAncor anchor = new GUIAncor(getState(), getWidth() - 49.0f, 28.0f);
			anchor.attach(buttonPane(a, anchor));
			row.expanded.add(new GUIListElement(anchor, getState()));
			row.expanded.attach(anchor);
			row.onInit();
			list.addWithoutUpdate(row);
		}
		list.updateDim();
	}

	private GUIHorizontalButtonTablePane buttonPane(final Annotation a, GUIAncor anchor) {
		GUIHorizontalButtonTablePane pane = new GUIHorizontalButtonTablePane(getState(), 6, 1, anchor);
		pane.onInit();

		pane.addButton(0, 0, "EDIT TEXT", HButtonColor.BLUE, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					editText(a);
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);

		pane.addButton(1, 0, "SIZE", HButtonColor.BLUE, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					LabelSize[] all = LabelSize.values();
					a.size = all[(a.getSize().ordinal() + 1) % all.length];
					store.save();
					redrawList();
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);

		pane.addButton(2, 0, a.visibility == Annotation.Visibility.HIDDEN ? "SHOW" : "HIDE",
				HButtonColor.YELLOW, new GUICallback() {
					@Override
					public void callback(GUIElement element, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							a.visibility = a.visibility == Annotation.Visibility.HIDDEN
									? Annotation.Visibility.ALWAYS
									: Annotation.Visibility.HIDDEN;
							store.save();
							redrawList();
						}
					}

					@Override
					public boolean isOccluded() {
						return false;
					}
				}, null);

		pane.addButton(3, 0, "LAYER", HButtonColor.BLUE, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					editLayer(a);
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);

		pane.addButton(4, 0, "LOCATE", HButtonColor.GREEN, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					//an annotation that is not drawn cannot flash, so clear whatever is
					//suppressing it first - its own flag, or its whole layer
					boolean changed = false;
					if(a.visibility == Annotation.Visibility.HIDDEN) {
						a.visibility = Annotation.Visibility.ALWAYS;
						changed = true;
					}
					if(store.isLayerHidden(a.layer)) {
						store.setLayerHidden(a.layer, false);
						changed = true;
					}
					if(changed) {
						store.save();
						redrawList();
					}
					a.flashHighlight();
				}
			}

			@Override
			public boolean isOccluded() {
				return false;
			}
		}, null);

		pane.addButton(5, 0, "DELETE", HButtonColor.RED, new GUICallback() {
			@Override
			public void callback(GUIElement element, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					store.remove(a);
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

	private void editLayer(final Annotation a) {
		new SimplePlayerTextInput("Move to layer", "Layer name") {
			@Override
			public boolean onInput(String input) {
				if(input == null || input.trim().isEmpty()) {
					return false;
				}
				a.layer = input.trim();
				store.save();
				redrawList();
				return true;
			}
		};
	}

	private void editText(final Annotation a) {
		new SimplePlayerTextInput("Edit annotation", "Text") {
			@Override
			public boolean onInput(String input) {
				if(input == null) {
					return false;
				}
				a.text = input.trim();
				store.save();
				redrawList();
				return true;
			}
		};
	}

	/** Dimensions carry their measurement rather than free text, so show that when empty. */
	private String describe(Annotation a) {
		if(a.text != null && !a.text.isEmpty()) {
			return a.text;
		}
		if(a.type == AnnotationType.DIMENSION) {
			int span = AnnotationGeometry.spanBlocks(a);
			if(span >= 0) {
				return "(" + span + " blocks)";
			}
			return "(measurement)";
		}
		return "(no text)";
	}

	private String position(Annotation a) {
		if(a.anchor == null) {
			return "-";
		}
		return Math.round(a.anchor.x) + "," + Math.round(a.anchor.y) + "," + Math.round(a.anchor.z);
	}

	private String label(String s) {
		//DIMENSION and LEADER_LABEL are wide in a narrow column; trim to something readable
		if("LEADER_LABEL".equals(s)) {
			return "Leader";
		}
		if("DIMENSION".equals(s)) {
			return "Dim";
		}
		return "Label";
	}

	private GUITextOverlayTable cell(String value) {
		GUITextOverlayTable t = new GUITextOverlayTable(10, 10, getState());
		t.setTextSimple(value);
		return t;
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	public class AnnotationRow extends ScrollableTableList<Annotation>.Row {
		public AnnotationRow(InputState state, Annotation a, GUIElement... elements) {
			super(state, a, elements);
			highlightSelect = true;
			highlightSelectSimple = true;
			setAllwaysOneSelected(true);
		}
	}
}
