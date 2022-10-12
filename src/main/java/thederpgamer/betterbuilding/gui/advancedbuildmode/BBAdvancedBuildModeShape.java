package thederpgamer.betterbuilding.gui.advancedbuildmode;

import api.common.GameClient;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildCallback;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.RemoveCallback;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.tools.CheckboxCallback;
import org.schema.game.client.view.gui.advanced.tools.CheckboxResult;
import org.schema.game.client.view.gui.advancedbuildmode.AdvancedBuildModeShape;
import org.schema.game.common.controller.DimensionFilter;
import org.schema.game.common.controller.EditableSendableSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableDirtyInterface;

import javax.vecmath.Vector3f;
import java.util.Objects;

/**
 * [Description]
 *
 * @author TheDerpGamer (MrGoose#0027)
 */
public class BBAdvancedBuildModeShape extends AdvancedBuildModeShape {

	public static boolean autoBuildEnabled = false;
	private static SegmentPiece selectedBlock;

	public BBAdvancedBuildModeShape(AdvancedGUIElement advancedGUIElement) {
		super(advancedGUIElement);
	}

	@Override
	public void build(GUIContentPane contentPane, GUIDockableDirtyInterface dockInterface) {
		super.build(contentPane, dockInterface);
		addCheckbox(contentPane.getContent(0), 1, 2, new CheckboxResult() {
			@Override
			public CheckboxCallback initCallback() {
				return new CheckboxCallback() {
					@Override
					public void onValueChanged(boolean b) {
						autoBuildEnabled = b;
					}
				};
			}

			@Override
			public String getName() {
				return "Auto Build";
			}

			@Override
			public String getToolTipText() {
				return "Automatically builds the shape on right click";
			}

			@Override
			public boolean getCurrentValue() {
				return autoBuildEnabled;
			}

			@Override
			public void setCurrentValue(boolean b) {
				autoBuildEnabled = b;
			}

			@Override
			public boolean getDefault() {
				return false;
			}
		});
	}

	public static void buildBlock(final Long index) {
		Vector3f pos = new Vector3f(Controller.getCamera().getPos());
		Vector3f dir = new Vector3f(Controller.getCamera().getForward());
		if(PlayerInteractionControlManager.isAdvancedBuildMode(GameClient.getClientState())) {
			dir = new Vector3f(GameClient.getClientState().getWorldDrawer().getAbsoluteMousePosition());
			dir.sub(pos);
		}
		dir.normalize();
		final PlayerInteractionControlManager interactionManager = getInteractionManager();

		InventorySlot slot = GameClient.getClientState().getPlayer().getInventory().getSlot(interactionManager.getSelectedSlot());
		if(slot != null && interactionManager.checkRadialSelect(slot.getType())) {
			interactionManager.buildBlock(getSegmentController(), pos, dir, new BuildCallback() {
				@Override
				public void onBuild(Vector3i posBuilt, Vector3i posNextToBuild, short typeBuilt) {
					GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildCommandManager().onBuiltBlock(posBuilt, posNextToBuild, typeBuilt);
					if(ElementKeyMap.getInfo(interactionManager.getSelectedTypeWithSub()).getControlledBy().contains(ElementKeyMap.CORE_ID)) {
						if(EngineSettings.G_AUTOSELECT_CONTROLLERS.isOn() && ElementKeyMap.getInfo(interactionManager.getSelectedTypeWithSub()).getControlling().size() > 0) {
							SegmentPiece pointUnsave = getSegmentController().getSegmentBuffer().getPointUnsave(posBuilt);
							if(pointUnsave != null) selectedBlock = pointUnsave;
						}
					} else if(selectedBlock != null && posBuilt != null) selectedBlock.refresh();
				}

				@Override
				public long getSelectedControllerPos() {
					if(selectedBlock != null) return selectedBlock.getAbsoluteIndex();
					else return Long.MIN_VALUE;
				}
			}, new DimensionFilter(), Objects.requireNonNull(getInteractionManager()).getPlayerCharacterManager().getSymmetryPlanes(), 300);
		}
	}

	public static void removeBlock(final long index) {
		Vector3f pos = new Vector3f(Controller.getCamera().getPos());
		Vector3f dir = new Vector3f(Controller.getCamera().getForward());

		if(PlayerInteractionControlManager.isAdvancedBuildMode(GameClient.getClientState())) {
			dir = new Vector3f(GameClient.getClientState().getWorldDrawer().getAbsoluteMousePosition());
			dir.sub(pos);
		}
		dir.normalize();
		GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().removeBlock(getSegmentController(), pos, dir, selectedBlock, 300, getInteractionManager().getPlayerCharacterManager().getSymmetryPlanes(), Element.TYPE_ALL, new RemoveCallback() {
			@Override
			public long getSelectedControllerPos() {
				if(selectedBlock != null) return selectedBlock.getAbsoluteIndex();
				else return Long.MIN_VALUE;
			}

			@Override
			public void onRemove(long pos, short type) {
				GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildCommandManager().onRemovedBlock(pos, type);
			}
		});
	}

	public static EditableSendableSegmentController getSegmentController() {
		try {
			return (EditableSendableSegmentController) GameClient.getCurrentControl();
		} catch (Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}

	public static PlayerInteractionControlManager getInteractionManager() {
		try {
			return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
		} catch (Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}
}
