package videogoose.betterbuilding.ui;

import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildSelection;
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

	/**
	 * First point of a dimension being built, held between the two clicks. Both ends must
	 * land on the same entity, since anchors are stored relative to their entity's core and
	 * a pair spanning two entities would have no fixed length.
	 */
	private Anchor pendingA;

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
		pane.addNewTextBox(30);

		buildTextEntry(pane);
		buildSizeAndCreate(pane);
		buildDimension(pane);
		buildStatus(pane);
	}

	private void buildDimension(GUIContentPane pane) {
		addButton(pane.getContent(0, 2), 0, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return pendingA == null ? HButtonColor.BLUE : HButtonColor.YELLOW;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						setDimensionStart();
					}

					@Override
					public void pressedRightMouse() {
						pendingA = null;
						status = "start point cleared";
					}
				};
			}

			@Override
			public String getName() {
				return pendingA == null ? "Dim: Set Start" : "Dim: Start Set";
			}

			@Override
			public String getToolTipText() {
				return "Left click: mark the block you are looking at as the measurement start.\n"
						+ "Right click: clear it.";
			}
		});

		addButton(pane.getContent(0, 2), 1, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.GREEN;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						finishDimension();
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Dim: Finish";
			}

			@Override
			public String getToolTipText() {
				return "Measure from the start point to the block you are looking at";
			}

			@Override
			public boolean isActive() {
				return pendingA != null;
			}
		});

		addButton(pane.getContent(0, 2), 2, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.BLUE;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						dimensionFromSelection();
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Dim: Selection";
			}

			@Override
			public String getToolTipText() {
				return "Measure across the current build mode selection box";
			}
		});
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
		addStatLabel(pane.getContent(0, 3), 0, 0, new StatLabelResult() {
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

		addButton(pane.getContent(0, 3), 1, 0, new ButtonResult() {
			@Override
			public HButtonColor getColor() {
				return HButtonColor.ORANGE;
			}

			@Override
			public ButtonCallback initCallback() {
				return new ButtonCallback() {
					@Override
					public void pressedLeftMouse() {
						openList();
					}

					@Override
					public void pressedRightMouse() {
					}
				};
			}

			@Override
			public String getName() {
				return "Manage...";
			}

			@Override
			public String getToolTipText() {
				return "List every annotation on this entity, to edit, hide, locate or delete";
			}
		});

		addStatLabel(pane.getContent(0, 3), 0, 1, new StatLabelResult() {
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

	private void setDimensionStart() {
		Anchor anchor = anchorAtTarget();
		if(anchor == null) {
			return;
		}
		pendingA = anchor;
		status = "start set, now aim at the far block";
	}

	private void finishDimension() {
		if(pendingA == null) {
			status = "set a start point first";
			return;
		}
		Anchor anchorB = anchorAtTarget();
		if(anchorB == null) {
			return;
		}
		if(!pendingA.entityKey.equals(anchorB.entityKey)) {
			status = "both ends must be on the same entity";
			return;
		}
		if(pendingA.blockIndex == anchorB.blockIndex) {
			status = "pick two different blocks";
			return;
		}
		createDimension(pendingA, anchorB);
		pendingA = null;
	}

	/**
	 * Builds a dimension across the current build mode selection box, so an existing
	 * region selection can be measured without re-picking both ends.
	 */
	private void dimensionFromSelection() {
		BuildSelection selection = getBuildToolsManager().getSelectMode();
		if(selection == null || selection.selectionBoxA == null || selection.selectionBoxB == null) {
			status = "no selection box";
			return;
		}
		SegmentController c = currentEntity();
		if(c == null || c.getUniqueIdentifier() == null) {
			status = "entity not ready";
			return;
		}
		Vector3i a = selection.selectionBoxA;
		Vector3i b = selection.selectionBoxB;
		if(a.equals(b)) {
			status = "selection is a single block";
			return;
		}
		createDimension(
				Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(), a.x, a.y, a.z),
				Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(), b.x, b.y, b.z));
	}

	private void createDimension(Anchor from, Anchor to) {
		Annotation a = Annotation.dimension(from, to);
		a.size = size;
		a.text = pendingText;
		store.add(a);
		status = "dimension added";
	}

	/**
	 * @return an anchor on the block currently targeted, or null with {@link #status} set
	 * to explain why not
	 */
	private Anchor anchorAtTarget() {
		SegmentPiece piece = getPlayerInteractionControlManager().getSelectedBlockByActiveController();
		if(piece == null) {
			status = "no block targeted";
			return null;
		}
		SegmentController c = piece.getSegmentController();
		if(c == null || c.getUniqueIdentifier() == null) {
			status = "entity not ready";
			return null;
		}
		Anchor anchor = Anchor.fromAbsoluteBlock(c.getUniqueIdentifier(),
				piece.getAbsolutePosX(), piece.getAbsolutePosY(), piece.getAbsolutePosZ());
		anchor.blockIndex = piece.getAbsoluteIndex();
		return anchor;
	}

	private void openList() {
		SegmentController c = currentEntity();
		if(c == null || c.getUniqueIdentifier() == null) {
			status = "no entity";
			return;
		}
		new AnnotationListDialog(store, c.getUniqueIdentifier()).activate();
		status = "";
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
