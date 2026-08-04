package videogoose.betterbuilding.ui;

import java.util.List;

import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.tools.ButtonCallback;
import org.schema.game.client.view.gui.advanced.tools.ButtonResult;
import org.schema.game.client.view.gui.advanced.tools.GUIAdvTextBar;
import org.schema.game.client.view.gui.advanced.tools.StatLabelResult;
import org.schema.game.client.view.gui.advanced.tools.TextBarCallback;
import org.schema.game.client.view.gui.advanced.tools.TextBarResult;
import org.schema.game.client.view.gui.advancedbuildmode.AdvancedBuildModeGUISGroup;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableDirtyInterface;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalArea.HButtonColor;

import videogoose.betterbuilding.annotation.Anchor;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;
import videogoose.betterbuilding.annotation.LabelSize;

/**
 * The "Annotations" section of the advanced build mode panel: the primary interface for
 * creating and managing annotations.
 * <p>
 * Type the text, pick a size, then click a button while looking at a block. Reading the
 * target block at click time rather than up front means the player can line up the shot
 * after typing, which is the natural order of operations.
 */
public class AnnotationBuildModeGroup extends AdvancedBuildModeGUISGroup {

	private final AnnotationStore store;

	private GUIAdvTextBar textBar;
	private String pendingText = "";
	private LabelSize size = LabelSize.MEDIUM;

	/** Shown in the status line until the next action replaces it. */
	private String status = "";

	public AnnotationBuildModeGroup(AdvancedGUIElement e, AnnotationStore store) {
		super(e);
		this.store = store;
	}

	@Override
	public String getId() {
		return "BB_ANNOTATIONS";
	}

	@Override
	public String getTitle() {
		return "Annotations";
	}

	@Override
	public boolean isDefaultExpanded() {
		return false;
	}

	@Override
	public void build(GUIContentPane pane, GUIDockableDirtyInterface dInt) {
		pane.setTextBoxHeightLast(30);
		pane.addNewTextBox(30);
		pane.addNewTextBox(30);
		pane.addNewTextBox(30);

		buildTextEntry(pane);
		buildSizeAndCreate(pane);
		buildStatus(pane);
	}

	private void buildTextEntry(GUIContentPane pane) {
		textBar = addTextBar(pane.getContent(0, 0), 0, 0, new TextBarResult() {
			@Override
			public String onTextChanged(String text) {
				pendingText = text == null ? "" : text.trim();
				return pendingText;
			}

			@Override
			public TextBarCallback initCallback() {
				return new TextBarCallback() {
					@Override
					public void onValueChanged(String value) {
						pendingText = value == null ? "" : value.trim();
					}
				};
			}

			@Override
			public String getName() {
				return "Label text";
			}

			@Override
			public String getToolTipText() {
				return "Text for the next annotation you create";
			}
		});
		textBar.setInactiveText("Label text");
		textBar.setMouseUpdateEnabled(true);
	}

	private void buildSizeAndCreate(GUIContentPane pane) {
		//size cycles on click rather than using a dropdown: four options do not justify
		//the dropdown's element plumbing, and one click is faster than two
		addButton(pane.getContent(0, 1), 0, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.BLUE;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						LabelSize[] all = LabelSize.values();
						size = all[(size.ordinal() + 1) % all.length];
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Size: " + size.getDisplayName();
			}

			@Override
			public String getToolTipText() {
				return "Click to cycle the text size for new annotations";
			}
		});

		addButton(pane.getContent(0, 1), 1, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.GREEN;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						create(false);
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Add Label";
			}

			@Override
			public String getToolTipText() {
				return "Pin the text to the block you are looking at";
			}
		});

		addButton(pane.getContent(0, 1), 2, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.GREEN;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						create(true);
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Add Leader";
			}

			@Override
			public String getToolTipText() {
				return "Pin the text offset from the block, with a line back to it";
			}
		});
	}

	private void buildStatus(GUIContentPane pane) {
		addStatLabel(pane.getContent(0, 2), 0, 0, new StatLabelResult() {
			@Override
			public String getName() {
				return "On this entity";
			}

			@Override
			public String getValue() {
				SegmentController c = currentEntity();
				if(c == null || c.getUniqueIdentifier() == null) {
					return "-";
				}
				return String.valueOf(store.forEntity(c.getUniqueIdentifier()).size());
			}

			@Override
			public int getStatDistance() {
				return 140;
			}
		});

		addButton(pane.getContent(0, 2), 1, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.ORANGE;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						removeLast();
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Remove Last";
			}

			@Override
			public String getToolTipText() {
				return "Delete the most recently added annotation on this entity";
			}
		});

		addStatLabel(pane.getContent(0, 2), 0, 1, new StatLabelResult() {
			@Override
			public String getName() {
				return "Status";
			}

			@Override
			public String getValue() {
				return status;
			}

			@Override
			public int getStatDistance() {
				return 140;
			}
		});
	}

	private void create(boolean leader) {
		if(pendingText.isEmpty()) {
			status = "enter some text first";
			return;
		}
		SegmentPiece piece = getPlayerInteractionControlManager().getSelectedBlockByActiveController();
		if(piece == null) {
			status = "no block targeted";
			return;
		}
		SegmentController c = piece.getSegmentController();
		if(c == null || c.getUniqueIdentifier() == null) {
			status = "entity not ready";
			return;
		}
		Anchor anchor = Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(),
				piece.getAbsolutePosX(), piece.getAbsolutePosY(), piece.getAbsolutePosZ());
		anchor.blockIndex = piece.getAbsoluteIndex();

		Annotation a = leader ? Annotation.leaderLabel(anchor, pendingText) : Annotation.label(anchor, pendingText);
		a.size = size;
		store.add(a);
		status = "added";
	}

	private void removeLast() {
		SegmentController c = currentEntity();
		if(c == null || c.getUniqueIdentifier() == null) {
			status = "no entity";
			return;
		}
		List<Annotation> list = store.forEntity(c.getUniqueIdentifier());
		if(list.isEmpty()) {
			status = "nothing to remove";
			return;
		}
		store.remove(list.get(list.size() - 1));
		status = "removed";
	}

	/**
	 * The entity being built on. Falls back to the targeted block's entity, so the counter
	 * still reads correctly when building on something other than the current ship.
	 */
	private SegmentController currentEntity() {
		SegmentPiece piece = getPlayerInteractionControlManager().getSelectedBlockByActiveController();
		if(piece != null && piece.getSegmentController() != null) {
			return piece.getSegmentController();
		}
		if(getState().getCurrentPlayerObject() instanceof SegmentController) {
			return (SegmentController) getState().getCurrentPlayerObject();
		}
		return null;
	}
}
