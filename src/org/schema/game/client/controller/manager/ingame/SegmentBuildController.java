//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.schema.game.client.controller.manager.ingame;

import com.bulletphysics.collision.dispatch.CollisionWorld.ClosestRayResultCallback;
import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.data.BuildData;
import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.FastMath;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.tutorial.states.ConnectedFromToTestState;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.camera.BuildShipCamera;
import org.schema.game.client.view.gui.buildtools.GUIOrientationSettingElement;
import org.schema.game.common.controller.CannotBeControlledException;
import org.schema.game.common.controller.DimensionFilter;
import org.schema.game.common.controller.NeighboringBlockCollection;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.ManagerModule;
import org.schema.game.common.controller.elements.UsableControllableSingleElementManager;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.camera.CameraMouseState;
import org.schema.schine.graphicsengine.camera.viewer.FixedViewer;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.KeyboardMappings;

/**
 * SegmentBuildocntroller.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public class SegmentBuildController extends AbstractBuildControlManager {
    public static final float EDIT_DISTANCE = 300.0F;
    public static Vector3f INITAL_BUILD_CAM_DIST = new Vector3f(0.0F, 0.0F, -2.0F);
    private final LongOpenHashSet done = new LongOpenHashSet(256);
    private final LongArrayList toDo = new LongArrayList(256);
    private final Vector3i tmpPos = new Vector3i();
    private final SegmentPiece tmpPiece = new SegmentPiece();
    Matrix4f tmpCMat = new Matrix4f();
    Matrix4f tmpSMat = new Matrix4f();
    Segment cachedLastSegment;
    int tmpN = 0;
    int mCol = 0;
    int nColMan = 0;
    ElementCollectionManager<?, ?, ?> lastCol;
    Vector3i mPos = new Vector3i();
    private BuildShipCamera shipBuildCamera;
    private EditSegmentInterface edit;
    private Vector3i currentlyCSelectedBlock;
    private SegmentPiece selectedBlock;
    private ClosestRayResultCallback currentNearestCollision;
    private Vector3i currentIntersectionElement = new Vector3i();
    private boolean elementIntersectionExists;
    private short currentElementType;
    private ArrayList<Vector3i> controllers = new ArrayList();
    private int controllerIndex;
    private boolean controllersDirty;
    private SegmentController lastShip;
    private long timeButtonPressed;
    private long lastNoteContBlock;
    private SegmentPiece currentSegmentPiece;

    public SegmentBuildController(GameClientState var1, EditSegmentInterface var2) {
        super(var1);
        this.edit = var2;
    }

    private void controlCurrentIntersectionBlock() {
        System.err.println("[CLIENT][SEGBUILDCONTROLLER] NORMAL CONNECTION");

        try {
            if (this.currentIntersectionElement != null) {
                if (this.getState().getController().getTutorialMode() != null && this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof ConnectedFromToTestState && !((ConnectedFromToTestState)this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState()).checkConnectToBlock(this.currentIntersectionElement, this.getState())) {
                    return;
                }

                this.edit.getSegmentController().setCurrentBlockController(this.selectedBlock, this.tmpPiece, ElementCollection.getIndex(this.currentIntersectionElement));
            }

        } catch (CannotBeControlledException var2) {
            this.handleConnotBuild(var2);
        }
    }

    private void controlCurrentIntersectionBlockBulk() {
        System.err.println("[CLIENT][SEGBUILDCONTROLLER] BULK CONNECTION");
        if (this.currentIntersectionElement == null || this.getState().getController().getTutorialMode() == null || !(this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof ConnectedFromToTestState) || ((ConnectedFromToTestState)this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState()).checkConnectToBlock(this.currentIntersectionElement, this.getState())) {
            try {
                NeighboringBlockCollection var1 = ((SegmentBufferManager)this.edit.getSegmentController().getSegmentBuffer()).getNeighborCollection(this.currentIntersectionElement);
                new Vector3i();
                int var19;
                if (this.selectedBlock != null && var1 != null && var1.getResult().size() > 0) {
                    System.err.println("[CLIENT][SEGBUILDCONTROLLER] BULK CONNECTING " + var1.getResult().size() + " elements of type " + var1.getType());
                    boolean var16 = this.edit.getSegmentController().getControlElementMap().isControlling(this.selectedBlock.getAbsoluteIndex(), this.currentIntersectionElement, var1.getType());
                    Iterator var20 = var1.getResult().iterator();

                    while(var20.hasNext()) {
                        long var21 = (Long)var20.next();
                        var19 = var16 ? 2 : 1;
                        this.edit.getSegmentController().setCurrentBlockController(this.selectedBlock, this.tmpPiece, var21, var19);
                    }

                } else {
                    if (this.selectedBlock != null && this.selectedBlock.getType() != 0 && ElementKeyMap.getInfo(this.selectedBlock.getType()).isSignal()) {
                        System.err.println("[CLIENT][SEGBUILDCONTROLLER] CHECKING SINGLE BLOCK MANAGERS FOR SIGNAL " + this.selectedBlock);
                        SegmentController var2 = this.selectedBlock.getSegment().getSegmentController();
                        boolean var3 = false;
                        int var18;
                        if (var2 instanceof ManagedSegmentController) {
                            Iterator var6 = ((ManagedSegmentController)var2).getManagerContainer().getModules().iterator();

                            label159:
                            while(true) {
                                ElementCollectionManager var8;
                                do {
                                    do {
                                        while(true) {
                                            ManagerModule var4;
                                            do {
                                                if (!var6.hasNext()) {
                                                    break label159;
                                                }
                                            } while(!((var4 = (ManagerModule)var6.next()).getElementManager() instanceof UsableControllableSingleElementManager));

                                            if ((var8 = ((UsableControllableSingleElementManager)var4.getElementManager()).getCollection()).isDetailedElementCollections()) {
                                                break;
                                            }

                                            System.err.println("[CLIENT] Cannot bulk link to non detailed collection. alternative search result: " + var1.getResult().size());
                                        }
                                    } while(var8.rawCollection == null);
                                } while(!var8.rawCollection.contains(ElementCollection.getIndex(this.currentIntersectionElement)));

                                Iterator var15 = var8.getElementCollections().iterator();

                                while(true) {
                                    ElementCollection var9;
                                    do {
                                        if (!var15.hasNext()) {
                                            var3 = true;
                                            continue label159;
                                        }
                                    } while(!(var9 = (ElementCollection)var15.next()).getNeighboringCollection().contains(ElementCollection.getIndex(this.currentIntersectionElement)));

                                    boolean var10;
                                    short var17;
                                    if (ElementKeyMap.isDoor(var17 = var9.getClazzId())) {
                                        var10 = this.edit.getSegmentController().getControlElementMap().isControlling(this.selectedBlock.getAbsoluteIndex(), this.currentIntersectionElement, ElementKeyMap.doorTypes);
                                    } else {
                                        var10 = this.edit.getSegmentController().getControlElementMap().isControlling(this.selectedBlock.getAbsoluteIndex(), this.currentIntersectionElement, var17);
                                    }

                                    Iterator var11 = var9.getNeighboringCollection().iterator();

                                    while(var11.hasNext()) {
                                        long var12 = (Long)var11.next();
                                        var18 = var10 ? 2 : 1;
                                        this.edit.getSegmentController().setCurrentBlockController(this.selectedBlock, this.tmpPiece, var12, var18);
                                    }
                                }
                            }
                        }

                        if (this.elementIntersectionExists && !var3 && ElementKeyMap.getInfo(this.currentElementType).canActivate()) {
                            SegmentPiece var5 = new SegmentPiece(this.currentSegmentPiece);

                            assert var5.getType() > 0 : var5;

                            this.done.clear();
                            this.toDo.clear();
                            this.done.add(var5.getAbsoluteIndex());
                            this.toDo.add(var5.getAbsoluteIndexWithType4());
                            var19 = this.edit.getSegmentController().getControlElementMap().isControlling(this.selectedBlock.getAbsoluteIndex(), this.currentIntersectionElement, this.currentElementType) ? 2 : 1;

                            label119:
                            while(true) {
                                long var7;
                                do {
                                    if (this.toDo.isEmpty()) {
                                        break label119;
                                    }
                                } while(ElementCollection.getType(var7 = this.toDo.removeLong(this.toDo.size() - 1)) != this.currentElementType);

                                long var22 = ElementCollection.getPosIndexFrom4(var7);
                                this.edit.getSegmentController().setCurrentBlockController(this.selectedBlock, this.tmpPiece, var22, var19);

                                for(var18 = 0; var18 < 6; ++var18) {
                                    var5.getAbsolutePos(this.tmpPos);
                                    this.tmpPos.add(Element.DIRECTIONSi[var18]);
                                    long var23 = ElementCollection.getIndex(this.tmpPos);
                                    if (!this.done.contains(var23)) {
                                        this.done.add(var23);
                                        SegmentPiece var13;
                                        if ((var13 = this.edit.getSegmentController().getSegmentBuffer().getPointUnsave(this.tmpPos, this.tmpPiece)) != null && var13.getType() == this.currentElementType) {
                                            assert var13.getAbsoluteIndex() == var23;

                                            this.toDo.add(var13.getAbsoluteIndexWithType4());
                                        }
                                    }
                                }
                            }
                        }

                        this.done.clear();
                        this.toDo.clear();
                    }

                }
            } catch (CannotBeControlledException var14) {
                this.handleConnotBuild(var14);
            }
        }
    }

    public Vector3i getCurrentBlock() {
        return this.currentlyCSelectedBlock;
    }

    public short getCurrentElementType() {
        return this.currentElementType;
    }

    public ClosestRayResultCallback getCurrentNearestCollision() {
        return this.currentNearestCollision;
    }

    public SegmentController getSegmentController() {
        return this.edit.getSegmentController();
    }

    public SegmentPiece getSelectedBlock() {
        return this.selectedBlock;
    }

    public ArrayList<SymmetryPlane> getXYPlanes() {
        return BuildData.xyPlanes;
    }

    public ArrayList<SymmetryPlane> getXZPlanes() {
        return BuildData.xzPlanes;
    }

    public ArrayList<SymmetryPlane> getYZPlanes() {
        return BuildData.yzPlanes;
    }

    public ArrayList<SymmetryPlane> getAllSymmetryPlanes() {
        return BuildData.getAllPlanes();
    }

    public SymmetryPlanes getSymmetryPlanes() {
        return this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().getSymmetryPlanes();
    }

    public void setPlaceMode(boolean placeMode) {
        for(SymmetryPlane symmetryPlane : getAllSymmetryPlanes()) {
            symmetryPlane.setPlaceMode(placeMode);
        }
    }

    public void handleKeyEvent(KeyEventInterface var1) {
        if (KeyboardMappings.getEventKeyState(var1, this.getState())) {
            if (this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().handleSlotKey(var1, KeyboardMappings.getEventKeySingle(var1))) {
                return;
            }

            if (KeyboardMappings.SHAPES_RADIAL_MENU.isEventKey(var1, this.getState())) {
                short var2 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedTypeWithSub();
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().openRadialSelectShapes(var2);
            }

            if (KeyboardMappings.isUndoButton(var1)) {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().undo();
            }

            if (KeyboardMappings.isRedoButton(var1)) {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().redo();
            }

            if (KeyboardMappings.BUILD_MODE_FLASHLIGHT.isEventKey(var1, this.getState())) {
                this.getState().getPlayer().getBuildModePosition().setFlashlightOnClient(!this.getState().getPlayer().getBuildModePosition().isFlashlightOn());
            } else if (KeyboardMappings.SELECT_MODULE.isEventKey(var1, this.getState())) {
                this.selectCameraBlock();
            } else if (!KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.NEXT_CONTROLLER.isEventKey(var1, this.getState())) {
                this.selectControllerBlock(true);
            } else if (!KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.PREVIOUS_CONTROLLER.isEventKey(var1, this.getState())) {
                this.selectControllerBlock(false);
            } else if (!KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.SELECT_CORE.isEventKey(var1, this.getState())) {
                this.selectCoreBlock(false);
            } else if (!KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.JUMP_TO_MODULE.isEventKey(var1, this.getState())) {
                this.jumpToCurrentBlock();
            } else if (!KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.CONNECT_MODULE.isEventKey(var1, this.getState())) {
                this.controlCurrentIntersectionBlock();
            } else if (KeyboardMappings.KEY_BULK_CONNECTION_MOD.isDown(this.getState()) && KeyboardMappings.CONNECT_MODULE.isEventKey(var1, this.getState())) {
                this.controlCurrentIntersectionBlockBulk();
            }

            if (PlayerInteractionControlManager.isAdvancedBuildMode(this.getState())) {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().handleKeyOrientation(var1);
            }
        }

        this.shipBuildCamera.handleKeyEvent(var1);
    }

    public void handleMouseEvent(MouseEvent var1) {
        if (System.currentTimeMillis() - this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSuspentionFreedTime() >= 300L) {
            BuildToolsManager var2;
            int var3;
            if ((var2 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).isInCreateDockingMode() && var2.getBuildToolCreateDocking().core != null && var2.getBuildToolCreateDocking().coreOrientation >= 0) {
                System.err.println("[CLIENT] create docking mode executing now");
                var2.getBuildToolCreateDocking().core.setOrientation((byte)var2.getBuildToolCreateDocking().coreOrientation);
                var2.getBuildToolCreateDocking().execute(this.getState());
                var2.cancelCreateDockingMode();
            } else if (var2.isInCreateDockingMode()) {
                var2.getBuildToolCreateDocking().potentialCoreOrientation = FastMath.cyclicModulo(var2.getBuildToolCreateDocking().potentialCoreOrientation + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), 32);
            } else {
                PlayerInteractionControlManager var4;
                if (PlayerInteractionControlManager.isAdvancedBuildMode(this.getState()) && ElementKeyMap.isValidType((var4 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager()).getSelectedTypeWithSub())) {
                    var3 = GUIOrientationSettingElement.getMaxRotation(var4);
                    var4.setBlockOrientation(FastMath.cyclicModulo(var4.getBlockOrientation() + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), var3));
                }
            }

            if (this.edit.getSegmentController() != null && Controller.getCamera() != null && !this.isSuspended()) {
                int var5 = EngineSettings.C_MOUSE_BUTTON_SWITCH.isOn() ? 1 : 0;
                var3 = EngineSettings.C_MOUSE_BUTTON_SWITCH.isOn() ? 0 : 1;
                if (var1.button == var5) {
                    this.buildButtonPressed(var1);
                }

                if (var1.button == var3 && !var1.state) {
                    this.removeButtonPressed(var1);
                }
            }

        }
    }

    public void onSwitch(boolean var1) {
        if (var1) {
            if (this.edit.getSegmentController() != this.lastShip) {
                this.currentlyCSelectedBlock = null;
                this.selectedBlock = null;
                this.lastShip = this.edit.getSegmentController();
            }

            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().load(this.edit.getSegmentController().getUniqueIdentifier());
            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().user = this.edit.getSegmentController().getUniqueIdentifier();
            this.edit.getSegmentController().getControlElementMap().setObs(this);
            this.controllers.clear();
            this.controllersDirty = true;
            if (this.shipBuildCamera != null && ((FixedViewer)this.shipBuildCamera.getViewable()).getEntity() == this.edit.getSegmentController()) {
                if (this.shipBuildCamera != null) {
                    this.shipBuildCamera.resetTransition(Controller.getCamera());
                }
            } else if (this.edit.getSegmentController() != null) {
                Transform var2 = new Transform(this.edit.getSegmentController().getWorldTransform());
                GlUtil.setUpVector(new Vector3f(0.0F, 1.0F, 0.0F), var2);
                GlUtil.setRightVector(new Vector3f(1.0F, 0.0F, 0.0F), var2);
                GlUtil.setForwardVector(new Vector3f(0.0F, 0.0F, 1.0F), var2);
                this.shipBuildCamera = new BuildShipCamera(this.getState(), Controller.getCamera(), this.edit, INITAL_BUILD_CAM_DIST, (Transform)null);
                if (this.edit.getEntered() != null) {
                    Vector3i var3;
                    Vector3i var10000 = var3 = this.edit.getEntered().getAbsolutePos(new Vector3i());
                    var10000.z -= 16;
                    this.shipBuildCamera.jumpToInstantly(var3);
                } else {
                    this.shipBuildCamera.jumpToInstantly(new Vector3i(16, 16, 0));
                }

                this.shipBuildCamera.setCameraStartOffset(0.0F);
            } else if (this.shipBuildCamera != null) {
                this.shipBuildCamera.resetTransition(Controller.getCamera());
            }

            this.getState().getController().timeOutBigMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_SEGMENTBUILDCONTROLLER_2);
            this.getState().getController().showBigMessage("BuildMode", Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_SEGMENTBUILDCONTROLLER_3, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_SEGMENTBUILDCONTROLLER_4, new Object[]{KeyboardMappings.CHANGE_SHIP_MODE.getKeyChar(), KeyboardMappings.ENTER_SHIP.getKeyChar()}), 0.0F);

            assert this.edit.getSegmentController() != null;

            Controller.setCamera(this.shipBuildCamera);
        } else {
            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().save(this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().user);
        }

        super.onSwitch(var1);
    }

    public void update(Timer var1) {
        if (this.selectedBlock != null) {
            this.selectedBlock.refresh();
            if (this.selectedBlock.getType() == 0) {
                this.selectedBlock = null;
            }
        }

        if (this.controllersDirty) {
            this.controllers.clear();
            Iterator var4 = this.edit.getSegmentController().getControlElementMap().getControllingMap().keySet().iterator();

            while(var4.hasNext()) {
                long var2 = (Long)var4.next();
                this.controllers.add(ElementCollection.getPosFromIndex(var2, new Vector3i()));
            }

            this.controllersDirty = false;
        }

        CameraMouseState.setGrabbed(!PlayerInteractionControlManager.isAdvancedBuildMode(this.getState()));
        if (this.edit.getSegmentController() != null) {
            this.updateNearsetIntersection();
        }

    }

    private void removeButtonPressed(MouseEvent var1) {
        Vector3f var3 = new Vector3f(Controller.getCamera().getPos());
        Vector3f var2 = new Vector3f(Controller.getCamera().getForward());
        if (PlayerInteractionControlManager.isAdvancedBuildMode(this.getState())) {
            (var2 = new Vector3f(this.getState().getWorldDrawer().getAbsoluteMousePosition())).sub(var3);
        }

        var2.normalize();
        this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().removeBlock(this.edit.getSegmentController(), var3, var2, this.selectedBlock, 300.0F, null, (short)32767, new RemoveCallback() {
            public long getSelectedControllerPos() {
                return SegmentBuildController.this.selectedBlock != null ? SegmentBuildController.this.selectedBlock.getAbsoluteIndex() : -9223372036854775808L;
            }

            public void onRemove(long var1, short var3) {
                SegmentBuildController.this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildCommandManager().onRemovedBlock(var1, var3);
            }
        });
    }

    private void buildButtonPressed(MouseEvent var1) {
        Vector3f var2 = new Vector3f(Controller.getCamera().getPos());
        Vector3f var3 = new Vector3f(Controller.getCamera().getForward());
        if (PlayerInteractionControlManager.isAdvancedBuildMode(this.getState())) {
            (var3 = new Vector3f(this.getState().getWorldDrawer().getAbsoluteMousePosition())).sub(var2);
        }

        var3.normalize();
        if (var1.state) {
            this.timeButtonPressed = System.currentTimeMillis();
        } else {
            this.timeButtonPressed = 0L;
        }

        PlayerInteractionControlManager var4 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
        InventorySlot var5 = this.getState().getPlayer().getInventory().getSlot(var4.getSelectedSlot());
        if (!var1.state) {
            final short var6 = var4.getSelectedTypeWithSub();
            if (var5 != null && var4.checkRadialSelect(var5.getType())) {
                return;
            }

            var4.buildBlock(this.edit.getSegmentController(), var2, var3, new BuildCallback() {
                public long getSelectedControllerPos() {
                    return SegmentBuildController.this.selectedBlock != null ? SegmentBuildController.this.selectedBlock.getAbsoluteIndex() : -9223372036854775808L;
                }

                public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                    SegmentBuildController.this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildCommandManager().onBuiltBlock(var1, var2, var3);
                    if (ElementKeyMap.getInfo(var6).getControlledBy().contains(Short.valueOf((short)1))) {
                        if (EngineSettings.G_AUTOSELECT_CONTROLLERS.isOn() && ElementKeyMap.getInfo(var6).getControlling().size() > 0) {
                            SegmentPiece var4;
                            if ((var4 = SegmentBuildController.this.edit.getSegmentController().getSegmentBuffer().getPointUnsave(var1)) != null) {
                                SegmentBuildController.this.selectedBlock = var4;
                            }

                            return;
                        }
                    } else if (SegmentBuildController.this.selectedBlock != null && var1 != null) {
                        SegmentBuildController.this.selectedBlock.refresh();
                        if (SegmentBuildController.this.selectedBlock.getType() == 0 && !ElementKeyMap.getInfo(var6).isRailTrack() && !ElementKeyMap.getInfo(var6).isSignal() && ElementKeyMap.getInfo(var6).getControlledBy().size() > 0 && System.currentTimeMillis() - SegmentBuildController.this.lastNoteContBlock > 1200000L) {
                            SegmentBuildController.this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_SEGMENTBUILDCONTROLLER_0, 0.0F);
                            SegmentBuildController.this.lastNoteContBlock = System.currentTimeMillis();
                            return;
                        }
                    } else if (SegmentBuildController.this.selectedBlock == null && ElementKeyMap.getInfo(var6).getControlledBy().size() > 0 && !ElementKeyMap.getInfo(var6).isRailTrack() && !ElementKeyMap.getInfo(var6).isSignal() && System.currentTimeMillis() - SegmentBuildController.this.lastNoteContBlock > 1200000L) {
                        SegmentBuildController.this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_SEGMENTBUILDCONTROLLER_1, 0.0F);
                        SegmentBuildController.this.lastNoteContBlock = System.currentTimeMillis();
                    }

                }
            }, new DimensionFilter(), null, 300.0F);
        }

    }

    private void jumpToCurrentBlock() {
        this.shipBuildCamera.jumpToInstantly(new Vector3i(16, 16, 0));
    }

    public void notifyElementChanged() {
        this.controllersDirty = true;
        this.getState().getWorldDrawer().getBuildModeDrawer().flagControllerSetChanged();
    }

    public void reset() {
    }

    public void setSelectedBlock(SegmentPiece var1) {
        this.selectedBlock = var1;
    }

    private void selectCameraBlock() {
        if (this.elementIntersectionExists && this.currentIntersectionElement != null) {
            if (this.selectedBlock != null && this.selectedBlock.getAbsolutePos(new Vector3i()).equals(this.currentIntersectionElement)) {
                this.selectedBlock = null;
                return;
            }

            this.selectedBlock = this.edit.getSegmentController().getSegmentBuffer().getPointUnsave(this.currentIntersectionElement);
            if (this.selectedBlock != null && this.getState().getController().getTutorialMode() != null && this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof ConnectedFromToTestState && !((ConnectedFromToTestState)this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState()).checkConnectBlock(this.selectedBlock, this.getState())) {
                this.selectedBlock = null;
            }
        }

    }

    private void selectControllerBlock(boolean var1) {
        if (!this.controllers.isEmpty()) {
            this.controllerIndex = FastMath.cyclicModulo(this.controllerIndex + (var1 ? 1 : -1), this.controllers.size() - 1);
            System.err.println("SWITCH " + var1 + " " + this.controllerIndex);
            SegmentPiece var2;
            if ((var2 = this.edit.getSegmentController().getSegmentBuffer().getPointUnsave((Vector3i)this.controllers.get(this.controllerIndex))) != null) {
                this.selectedBlock = var2;
            }

        }
    }

    private void selectCoreBlock(boolean var1) {
        if (!this.controllers.isEmpty()) {
            SegmentPiece var2;
            if ((var2 = this.edit.getSegmentController().getSegmentBuffer().getPointUnsave(this.edit.getCore())) != null) {
                this.selectedBlock = var2;
                this.controllerIndex = Math.max(0, this.controllers.indexOf(this.edit.getCore()));
            }

        }
    }

    private void updateNearsetIntersection() {
        Vector3f var1 = new Vector3f(Controller.getCamera().getPos());
        Vector3f var2;
        if (!Float.isNaN((var2 = new Vector3f(Controller.getCamera().getForward())).x)) {
            Vector3f var3;
            if (PlayerInteractionControlManager.isAdvancedBuildMode(this.getState())) {
                var3 = new Vector3f(this.getState().getWorldDrawer().getAbsoluteMousePosition());
                var2.sub(var3, var1);
            }

            var2.normalize();
            var2.scale(300.0F);
            (var3 = new Vector3f(var1)).add(var2);
            this.currentNearestCollision = ((PhysicsExt)this.getState().getPhysics()).testRayCollisionPoint(var1, var3, false, (SimpleTransformableSendableObject)null, this.edit.getSegmentController(), true, true, false);
            if (this.currentNearestCollision != null && this.currentNearestCollision.hasHit() && this.currentNearestCollision instanceof CubeRayCastResult) {
                CubeRayCastResult var4;
                if ((var4 = (CubeRayCastResult)this.currentNearestCollision).getSegment() != null && var4.getCubePos() != null) {
                    var4.getSegment().getAbsoluteElemPos(var4.getCubePos(), this.currentIntersectionElement);
                    this.currentElementType = var4.getSegment().getSegmentData().getType(var4.getCubePos());
                    this.cachedLastSegment = var4.getSegment();
                    this.currentSegmentPiece = new SegmentPiece(var4.getSegment(), var4.getCubePos());
                    this.elementIntersectionExists = true;
                } else {
                    this.cachedLastSegment = null;
                    this.currentSegmentPiece = null;
                    this.elementIntersectionExists = false;
                }
            } else {
                this.cachedLastSegment = null;
                this.currentSegmentPiece = null;
                this.elementIntersectionExists = false;
            }
        }
    }

    public SegmentPiece getCurrentSegmentPiece() {
        return this.currentSegmentPiece;
    }

    public BuildShipCamera getShipBuildCamera() {
        return this.shipBuildCamera;
    }
}