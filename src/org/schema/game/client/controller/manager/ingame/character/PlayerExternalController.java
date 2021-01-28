package org.schema.game.client.controller.manager.ingame.character;

import com.bulletphysics.collision.dispatch.CollisionWorld.ClosestRayResultCallback;
import com.bulletphysics.linearmath.Transform;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Observer;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.FastMath;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.PlayerGameTextInput;
import org.schema.game.client.controller.manager.ingame.AbstractBuildControlManager;
import org.schema.game.client.controller.manager.ingame.BuildCallback;
import org.schema.game.client.controller.manager.ingame.PlayerGameControlManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.RemoveCallback;
import org.schema.game.client.controller.manager.ingame.SymmetryPlanes;
import org.schema.game.client.controller.tutorial.states.ConnectedFromToTestState;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.data.PlayerControllable;
import org.schema.game.client.view.camera.PlayerCamera;
import org.schema.game.client.view.cubes.occlusion.Occlusion;
import org.schema.game.client.view.gui.buildtools.GUIOrientationSettingElement;
import org.schema.game.common.controller.CannotBeControlledException;
import org.schema.game.common.controller.DimensionFilter;
import org.schema.game.common.controller.EditableSendableSegmentController;
import org.schema.game.common.controller.ElementPositionBlockedException;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.Planet;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ShopSpaceStation;
import org.schema.game.common.controller.SpaceStation;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.player.ControllerStateUnit;
import org.schema.game.common.data.player.InteractionInterface;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.SimplePlayerCommands;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.game.common.data.world.GameTransformable;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.network.objects.InterconnectStructureRequest;
import org.schema.game.network.objects.remote.RemoteInterconnectStructure;
import org.schema.game.server.controller.world.factory.WorldCreatorShopMannedFactory;
import org.schema.game.server.data.EntityRequest;
import org.schema.schine.common.InputChecker;
import org.schema.schine.common.TextCallback;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.CameraMouseState;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;

/**
 * PlayerExternalController.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public class PlayerExternalController extends AbstractBuildControlManager {
    public static final float EDIT_DISTANCE = 6.0F;
    private final ArrayList<SymmetryPlane> xyPlanes = new ArrayList<>();
    private final ArrayList<SymmetryPlane> xzPlanes = new ArrayList<>();
    private final ArrayList<SymmetryPlane> yzPlanes = new ArrayList<>();
    private final long suckProtectMillisDuration = 8000L;
    private final SegmentPiece tmpPiece = new SegmentPiece();
    String description;
    private Camera followerCamera;
    private SegmentPiece selectedBlock;
    private boolean controllersDirty;
    private String last;
    private Vector3i posM;
    private Vector3i toSelectBox;
    private SegmentController toSelectSegController;
    private long toSelectTime;

    public PlayerExternalController(GameClientState var1) {
        super(var1);
        this.description = Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_0;
        this.posM = new Vector3i();
    }

    public synchronized void addObserver(Observer var1) {
        super.addObserver(var1);
    }

    public boolean canEnter(short var1) {
        return ElementKeyMap.getInfo(var1).isEnterable();
    }

    public void sit() {
        if (this.getState().getPlayer().isSitting()) {
            System.err.println("[CLIENT][SIT] standing up");
            this.getState().getPlayer().sendSimpleCommand(SimplePlayerCommands.SIT_DOWN, new Object[]{-1, 0L, 0L, 0L});
        } else {
            this.getState().getCharacter().sitDown(this.getNearestIntersection(), this.getState().getCharacter().getHeadWorldTransform().origin, Controller.getCamera().getForward(), 6.0F);
        }
    }

    public void checkAddAndRemove(boolean var1) throws ElementPositionBlockedException {
        PlayerInteractionControlManager var2 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
        InventorySlot var4 = this.getState().getPlayer().getInventory().getSlot(var2.getSelectedSlot());
        short var3 = var2.getSelectedTypeWithSub();
        System.err.println("[CLIENT][ASTRONAUTMODE] Selected Type: " + ElementKeyMap.toString(var3) + "; Slot: " + (var4 != null ? var4.getType() : "NULL"));
        if (!var1 || var4 == null || !var2.checkRadialSelect(var4.getType())) {
            if (var4 != null && (var3 >= 0 || var3 == -32768)) {
                if (var1 && var3 == 1) {
                    this.spawnShip();
                } else {
                    ClosestRayResultCallback var6;
                    if ((var6 = this.getNearestIntersection()) != null && var6.hasHit() && var6 instanceof CubeRayCastResult) {
                        final CubeRayCastResult var7;
                        if ((var7 = (CubeRayCastResult)var6).getSegment() != null && var7.getSegment().getSegmentController() instanceof EditableSendableSegmentController) {
                            var7.getSegment().getSegmentController().getControlElementMap().setObs(this);
                            if (var7.getSegment().getSegmentController().isVirtualBlueprint()) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_1, 0.0F);
                                return;
                            }

                            Vector3f var8 = new Vector3f(this.getState().getCharacter().getHeadWorldTransform().origin);
                            Vector3f var5 = new Vector3f(Controller.getCamera().getForward());
                            if (var1) {
                                if (this.getState().getPlayer().isInTutorial() && ElementKeyMap.isValidType(var3) && !ElementKeyMap.getInfo(var3).isRailTrack() && ElementKeyMap.getInfo(var3).getControlling().size() > 0 && var7.getSegment().getSegmentController() instanceof Ship) {
                                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_2, 0.0F);
                                    return;
                                }

                                if (var3 == 689) {
                                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_34, 0.0F);
                                    return;
                                }

                                System.err.println("[CLIENT][ExternalController] adding block to segment: " + var7.getSegment() + "; " + var7.getSegment().getSegmentController().getSegmentBuffer().get(var7.getSegment().pos));
                                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().buildBlock((EditableSendableSegmentController)var7.getSegment().getSegmentController(), var8, var5, new BuildCallback() {
                                    public long getSelectedControllerPos() {
                                        return PlayerExternalController.this.selectedBlock != null ? PlayerExternalController.this.selectedBlock.getAbsoluteIndex() : -9223372036854775808L;
                                    }

                                    public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                                        Occlusion.dbPos.set(var1);
                                        if (ElementKeyMap.isValidType(var3) && !ElementKeyMap.getInfo(var3).isRailTrack() && ElementKeyMap.getInfo(var3).getControlling().size() > 0 && var7.getSegment().getSegmentController() instanceof Ship) {
                                            PlayerExternalController.this.toSelectBox = var1;
                                            PlayerExternalController.this.toSelectSegController = var7.getSegment().getSegmentController();
                                            PlayerExternalController.this.toSelectTime = System.currentTimeMillis();
                                        }

                                    }
                                }, new DimensionFilter(), this.getAllSymmetryPlanes(), 6.0F);
                                return;
                            }

                            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().removeBlock((EditableSendableSegmentController)var7.getSegment().getSegmentController(), var8, var5, this.selectedBlock, 6.0F, this.getAllSymmetryPlanes(), (short)1, new RemoveCallback() {
                                public long getSelectedControllerPos() {
                                    return PlayerExternalController.this.selectedBlock != null ? PlayerExternalController.this.selectedBlock.getAbsoluteIndex() : -9223372036854775808L;
                                }

                                public void onRemove(long var1, short var3) {
                                }
                            });
                        }

                    } else {
                        System.err.println("[PLAYEREXTERNALEFT_CONTROLLER] CUBE RESULT NOT AVAILABLE");
                    }
                }
            }
        }
    }

    public ArrayList<SymmetryPlane> getAllSymmetryPlanes() {
        ArrayList<SymmetryPlane> allPlanes = new ArrayList<>();
        allPlanes.addAll(xyPlanes);
        allPlanes.addAll(xzPlanes);
        allPlanes.addAll(yzPlanes);
        return allPlanes;
    }

    public void setPlaceMode(boolean placeMode) {
        for(SymmetryPlane symmetryPlane : getAllSymmetryPlanes()) {
            symmetryPlane.setPlaceMode(placeMode);
        }
    }

    public boolean checkEnterDry(SegmentPiece var1, boolean var2) {
        if (var1 != null && !this.getState().getController().allowedToActivate(var1)) {
            return false;
        } else {
            SegmentController var3 = var1.getSegmentController();
            if (!(var1.getSegment().getSegmentController() instanceof PlayerControllable)) {
                if (var2) {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_29, 0.0F);
                }

                return false;
            } else if (var3 instanceof SpaceStation && this.getState().getPlayer().isInTutorial()) {
                if (var2) {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_28, 0.0F);
                }

                return false;
            } else {
                int var4;
                if ((var4 = var3.getFactionId()) == 0 && !this.isAllowedToBuildAndSpawnShips()) {
                    if (var2) {
                        this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_30, 0.0F);
                    }

                    return false;
                } else if (var4 != 0 && var4 == this.getState().getPlayer().getFactionId() && !var3.isSufficientFactionRights(this.getState().getPlayer())) {
                    if (var2) {
                        this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_31 + "faction rank!", 0.0F);
                    }

                    return false;
                } else if (var3.isCoreOverheating()) {
                    this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getInShipControlManager().popupShipRebootDialog(var3);
                    return false;
                } else if (var3.getHpController().isRebootingRecoverFromOverheating()) {
                    if (var2) {
                        this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_32, new Object[]{StringTools.formatTimeFromMS(var3.getHpController().getRebootTimeLeftMS())}), 0.0F);
                    }

                    return false;
                } else if (this.canEnter(var1.getType())) {
                    return true;
                } else {
                    if (var2) {
                        this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_11, 0.0F);
                    }

                    return false;
                }
            }
        }
    }

    public boolean checkEnter(SegmentPiece var1) {
        if (var1 != null && !this.getState().getController().allowedToActivate(var1)) {
            return false;
        } else {
            SegmentController var2 = var1.getSegmentController();
            if (!this.checkEnterDry(var1, true)) {
                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_27, 0.0F);
                return false;
            } else {
                PlayerInteractionControlManager var3 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
                if (var2 instanceof Ship) {
                    if (var1.getType() == 1 && !((Ship)var2).getAttachedPlayers().isEmpty()) {
                        Iterator var4 = ((PlayerState)((Ship)var2).getAttachedPlayers().get(0)).getControllerState().getUnits().iterator();

                        while(var4.hasNext()) {
                            ControllerStateUnit var5;
                            if ((var5 = (ControllerStateUnit)var4.next()).parameter != null && var5.parameter.equals(Ship.core)) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_9, 0.0F);
                                return false;
                            }
                        }
                    }
                } else if (var2 instanceof SpaceStation) {
                    var3.getSegmentControlManager().setEntered(var1);
                } else if (var2 instanceof Planet) {
                    var3.getSegmentControlManager().setEntered(var1);
                } else if (var2 instanceof FloatingRock) {
                    var3.getSegmentControlManager().setEntered(var1);
                }

                return true;
            }
        }
    }

    public boolean checkEnterAndEnterIfPossible(SegmentPiece var1) {
        if (var1 != null && !this.getState().getController().allowedToActivate(var1)) {
            return false;
        } else if (var1.getSegment().getSegmentController() instanceof SpaceStation && this.getState().getPlayer().isInTutorial()) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_4, 0.0F);
            return false;
        } else if (!(var1.getSegment().getSegmentController() instanceof PlayerControllable)) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_5, 0.0F);
            return false;
        } else {
            int var2;
            if ((var2 = var1.getSegment().getSegmentController().getFactionId()) == 0 && !this.isAllowedToBuildAndSpawnShips()) {
                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_6, 0.0F);
                return false;
            } else if (var2 != 0 && var2 == this.getState().getPlayer().getFactionId() && !var1.getSegmentController().isSufficientFactionRights(this.getState().getPlayer())) {
                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_7 + "faction rank!", 0.0F);
                return false;
            } else if (var1.getSegmentController().isCoreOverheating()) {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getInShipControlManager().popupShipRebootDialog(var1.getSegmentController());
                return false;
            } else if (var1.getSegmentController().getHpController().isRebootingRecoverFromOverheating()) {
                this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_8, new Object[]{StringTools.formatTimeFromMS(var1.getSegmentController().getHpController().getRebootTimeLeftMS())}), 0.0F);
                return false;
            } else if (!this.canEnter(var1.getType())) {
                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_37, 0.0F);
                return false;
            } else {
                PlayerInteractionControlManager var5 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
                if (var1.getSegment().getSegmentController() instanceof Ship) {
                    if (var1.getType() == 1 && !((Ship)var1.getSegment().getSegmentController()).getAttachedPlayers().isEmpty()) {
                        Iterator var3 = ((PlayerState)((Ship)var1.getSegment().getSegmentController()).getAttachedPlayers().get(0)).getControllerState().getUnits().iterator();

                        while(var3.hasNext()) {
                            ControllerStateUnit var4;
                            if ((var4 = (ControllerStateUnit)var3.next()).parameter != null && var4.parameter.equals(Ship.core)) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_33, 0.0F);
                                return false;
                            }
                        }
                    }

                    var5.getInShipControlManager().setEntered(var1);
                } else if (var1.getSegment().getSegmentController() instanceof SpaceStation) {
                    var5.getSegmentControlManager().setEntered(var1);
                } else if (var1.getSegment().getSegmentController() instanceof Planet) {
                    var5.getSegmentControlManager().setEntered(var1);
                } else {
                    if (!(var1.getSegment().getSegmentController() instanceof FloatingRock)) {
                        throw new RuntimeException("Cannot enter " + var1.getSegment().getSegmentController());
                    }

                    var5.getSegmentControlManager().setEntered(var1);
                }

                System.err.println("[CLIENT] Player character enter used");
                this.getState().currentEnterTry = var1.getSegment().getSegmentController();
                this.getState().currentEnterTryTime = System.currentTimeMillis();
                this.getState().getController().requestControlChange(this.getState().getCharacter(), (PlayerControllable)var1.getSegmentController(), new Vector3i(), var1.getAbsolutePos(new Vector3i()), true);
                float var6;
                if (var1.getSegmentController().railController.isDockedAndExecuted() && (var6 = var1.getSegmentController().railController.getRailMassPercent()) < 1.0F) {
                    this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_10, new Object[]{StringTools.formatPointZero(var6 * 100.0F)}), 0.0F);
                }

                return true;
            }
        }
    }

    private void checkPhysicalConnectionToCore() {
    }

    private void controlCurrentIntersectionBlock() {
        if (this.selectedBlock != null) {
            if (ElementKeyMap.isValidType(this.selectedBlock.getType())) {
                ClosestRayResultCallback var1;
                CubeRayCastResult var3;
                if ((var1 = this.getNearestIntersection()) != null && var1.hasHit() && var1 instanceof CubeRayCastResult && (var3 = (CubeRayCastResult)var1).getSegment() != null && var3.getSegment().getSegmentController() instanceof EditableSendableSegmentController) {
                    SegmentPiece var5;
                    if (var3.getSegment().getSegmentController() == this.selectedBlock.getSegmentController()) {
                        if (this.getState().getController().allowedToConnect(var3.getSegment().getSegmentController())) {
                            Vector3i var4 = var3.getSegment().getAbsoluteElemPos(var3.getCubePos(), new Vector3i());
                            if (this.getState().getController().getTutorialMode() != null && this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof ConnectedFromToTestState && !((ConnectedFromToTestState)this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState()).checkConnectToBlock(var4, this.getState())) {
                                return;
                            }

                            try {
                                if ((var5 = this.selectedBlock.getSegmentController().getSegmentBuffer().getPointUnsave(var4)) != null && var5.getType() > 0) {
                                    this.selectedBlock.getSegmentController().setCurrentBlockController(this.selectedBlock, this.tmpPiece, var5.getAbsoluteIndex());
                                    return;
                                }

                                System.err.println("[CLIENT][PlayerExternal][Connect] WARNING: intersection ok, but intersected with 0 type " + var5);
                                return;
                            } catch (CannotBeControlledException var2) {
                                this.handleConnotBuild(var2);
                                return;
                            }
                        }
                    } else {
                        var3.getSegment().getAbsoluteElemPos(var3.getCubePos(), new Vector3i());
                        var5 = new SegmentPiece(var3.getSegment(), var3.getCubePos());
                        if (this.selectedBlock.getType() == 668 && var5.getType() == 668) {
                            InterconnectStructureRequest var6 = new InterconnectStructureRequest(this.selectedBlock, var5, this.getState().getPlayer().getId());
                            this.selectedBlock.getSegmentController().getNetworkObject().structureInterconnectRequestBuffer.add(new RemoteInterconnectStructure(var6, false));
                        }
                    }
                }

                return;
            }

            System.err.println("[CLIENT][PlayerExternal][Connect] WARNING: selected block is type 0 ");
        }

    }

    public boolean getHideConditions() {
        PlayerGameControlManager var1;
        return (var1 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager()).getPlayerIntercationManager().getInShipControlManager().isActive() || var1.getPlayerIntercationManager().getInShipControlManager().isDelayedActive();
    }

    public ClosestRayResultCallback getNearestIntersection() {
        Vector3f var1 = new Vector3f(this.getState().getCharacter().getHeadWorldTransform().origin);
        Vector3f var2 = new Vector3f(this.getState().getCharacter().getHeadWorldTransform().origin);
        Vector3f var3;
        (var3 = new Vector3f(Controller.getCamera().getForward())).scale(6.0F);
        var2.add(var3);
        CubeRayCastResult var4;
        (var4 = new CubeRayCastResult(var1, var2, false, new SegmentController[0])).setIgnoereNotPhysical(true);
        var4.setOnlyCubeMeshes(true);
        return ((PhysicsExt)this.getState().getPhysics()).testRayCollisionPoint(var1, var2, var4, false);
    }

    public SegmentPiece getSelectedBlock() {
        return this.selectedBlock;
    }

    public void handleKeyEvent(KeyEventInterface var1) {
        super.handleKeyEvent(var1);
        if (KeyboardMappings.getEventKeyState(var1, this.getState())) {
            if (this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().handleSlotKey(var1, KeyboardMappings.getEventKeySingle(var1))) {
                return;
            }

            if (KeyboardMappings.SIT_ASTRONAUT.isEventKey(var1, this.getState())) {
                this.sit();
            }

            KeyboardMappings.CREW_CONTROL.isDown(this.getState());
            if (KeyboardMappings.STUCK_PROTECT.isEventKey(var1, this.getState())) {
                long var2 = System.currentTimeMillis() - this.getState().getCharacter().getActivatedStuckProtection();
                if (this.getState().getCharacter().getActivatedStuckProtection() > 0L && var2 < 8000L) {
                    if (this.last != null) {
                        this.getState().getController().endPopupMessage(this.last);
                    }

                    if (this.getState().getCharacter() != null) {
                        this.getState().getCharacter().flagWapOutOfClient(1);
                    }

                    this.last = null;
                    this.getState().getCharacter().setActivatedStuckProtection(0L);
                } else {
                    System.err.println("[CLIENT] CHECKING GRAV UP FOR DIFF OBJECT ::: in grav: " + this.getState().getCharacter().getGravity().isGravityOn() + "; diff obj touched: " + this.getState().getCharacter().getGravity().differentObjectTouched);
                    if (this.getState().getCharacter().getGravity().isGravityOn() && this.getState().getCharacter().getGravity().differentObjectTouched) {
                        this.getState().getCharacter().scheduleGravity(new Vector3f(0.0F, 0.0F, 0.0F), (SimpleTransformableSendableObject)null);
                        this.getState().getCharacter().getGravity().differentObjectTouched = false;
                    }
                }
            }

            if (KeyboardMappings.getEventKeySingle(var1) == 65) {
                this.checkPhysicalConnectionToCore();
            }

            int var6;
            if (KeyboardMappings.getEventKeySingle(var1) == 205) {
                var6 = this.getState().getPlayer().getNetworkObject().playerFaceId.get();
                this.getState().getPlayer().getNetworkObject().playerFaceId.set(FastMath.cyclicModulo(var6 + 1, 3), true);
            }

            if (KeyboardMappings.getEventKeySingle(var1) == 203) {
                var6 = this.getState().getPlayer().getNetworkObject().playerFaceId.get();
                this.getState().getPlayer().getNetworkObject().playerFaceId.set(FastMath.cyclicModulo(var6 - 1, 3), true);
            }

            if (KeyboardMappings.SHAPES_RADIAL_MENU.isEventKey(var1, this.getState())) {
                short var7 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedTypeWithSub();
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().openRadialSelectShapes(var7);
            }

            if (KeyboardMappings.ACTIVATE.isEventKey(var1, this.getState())) {
                Object var8 = this.getState().getCharacter().getNearestEntity(true);
                System.err.println("[CLIENT] ACTIVATE::: " + var8);
                if (!(var8 instanceof SegmentController)) {
                    assert var8 != this.getState().getCharacter();

                    System.err.println("[PlayerExternal] nearest: " + var8 + "; (interaction: " + (var8 instanceof InteractionInterface) + ")");
                    if (var8 instanceof InteractionInterface) {
                        ((InteractionInterface)var8).interactClient(this.getState().getPlayer());
                    }

                    return;
                }

                SegmentPiece var3;
                if ((var3 = this.getState().getCharacter().getNearestPiece(true)) != null && var3.getType() == 679) {
                    Iterator var5 = var3.getSegmentController().railController.next.iterator();

                    while(var5.hasNext()) {
                        RailRelation var4;
                        if ((var4 = (RailRelation)var5.next()).rail.getAbsoluteIndex() == var3.getAbsoluteIndex()) {
                            if ((var3 = var4.docked.getSegmentController().getSegmentBuffer().getPointUnsave(var4.docked.getAbsoluteIndex())) == null) {
                                return;
                            }

                            var8 = var3.getSegmentController();
                            break;
                        }
                    }
                }

                if (var8 instanceof ShopSpaceStation && var3.equalsPos(WorldCreatorShopMannedFactory.shopkeepSpawner)) {
                    this.getState().getPlayer().sendSimpleCommand(SimplePlayerCommands.SPAWN_SHOPKEEP, new Object[]{((GameTransformable)var8).getId()});
                    return;
                }

                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().checkActivate(var3);
                return;
            }

            if (KeyboardMappings.SELECT_MODULE.isEventKey(var1, this.getState())) {
                this.selectCameraBlock();
                return;
            }

            if (KeyboardMappings.CONNECT_MODULE.isEventKey(var1, this.getState())) {
                this.controlCurrentIntersectionBlock();
                return;
            }

            if (!KeyboardMappings.ASTRONAUT_ROTATE_BLOCK.isDown(this.getState())) {
                if (KeyboardMappings.SPAWN_SHIP.isEventKey(var1, this.getState())) {
                    this.spawnShip();
                    return;
                }

                if (KeyboardMappings.SPAWN_SPACE_STATION.isEventKey(var1, this.getState())) {
                    this.spawnStation();
                }
            }
        }

    }

    private void spawnStation() {
        if (!this.isAllowedToBuildAndSpawnShips()) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_36, 0.0F);
        } else {
            SegmentPiece var1;
            if ((var1 = this.getState().getCharacter().getNearestPiece(false)) != null) {
                if (var1.getSegment().getSegmentController().isScrap()) {
                    long var2 = var1.getSegment().getSegmentController().getElementClassCountMap().getPrice();
                    if ((long)this.getState().getPlayer().getCredits() < var2) {
                        this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_17, new Object[]{var2}), 0.0F);
                    } else {
                        this.repairSpaceStation(var1, var2);
                    }
                } else {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_18, 0.0F);
                }
            } else if (this.getState().getPlayer().getCredits() < this.getState().getGameState().getStationCost()) {
                this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_19, new Object[]{StringTools.formatSeperated(this.getState().getGameState().getStationCost())}), 0.0F);
            } else {
                this.buildSpaceStation();
            }
        }
    }

    private void spawnShip() {
        if (!this.isAllowedToBuildAndSpawnShips()) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_16, 0.0F);
        } else if (!this.getState().getPlayer().getInventory((Vector3i)null).existsInInventory((short)1)) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_13, 0.0F);
        } else if (this.getState().getCharacter().getNearestPiece(false) != null) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_14, 0.0F);
        } else {
            PlayerGameTextInput var1;
            (var1 = new PlayerGameTextInput("PlayerExternalController_NEW_ENTITY_NAME", this.getState(), 50, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_15, this.description, this.getState().getPlayerName() + "_" + System.currentTimeMillis()) {
                public String[] getCommandPrefixes() {
                    return null;
                }

                public boolean isOccluded() {
                    return this.getState().getController().getPlayerInputs().indexOf(this) != this.getState().getController().getPlayerInputs().size() - 1;
                }

                public String handleAutoComplete(String var1, TextCallback var2, String var3) {
                    return var1;
                }

                public void onDeactivate() {
                    System.err.println("deactivate");
                    PlayerExternalController.this.suspend(false);
                }

                public void onFailedTextCheck(String var1) {
                    this.setErrorMessage("SHIPNAME INVALID: " + var1);
                }

                public boolean onInput(String var1) {
                    if (this.getState().getCharacter() != null && this.getState().getCharacter().getPhysicsDataContainer() != null && this.getState().getCharacter().getPhysicsDataContainer().isInitialized()) {
                        Transform var2 = new Transform();
                        Vector3f var3 = new Vector3f(Controller.getCamera().getForward());
                        var2.set(this.getState().getCharacter().getPhysicsDataContainer().getCurrentPhysicsTransform());
                        var3.scale(2.0F);
                        var2.origin.add(var3);
                        var2.basis.rotY(-1.5707964F);
                        if (this.getState().getPlayer().getInventory((Vector3i)null).existsInInventory((short)1)) {
                            if (!var1.toLowerCase(Locale.ENGLISH).contains("vehicle")) {
                                String var4 = EntityRequest.convertShipEntityName(var1.trim());
                                this.getState().getController().requestNewShip(var2, new Vector3i(-2, -2, -2), new Vector3i(2, 2, 2), this.getState().getPlayer(), var4, var1.trim());
                                System.err.println("SENDING LAST NAME: " + var4 + "; obs: " + PlayerExternalController.this.countObservers());
                                PlayerExternalController.this.sendLastShipName(var4);
                            }
                        } else {
                            this.getState().getController().popupAlertTextMessage("ERROR\nYou need a ship core\nto create a ship", 0.0F);
                        }

                        return true;
                    } else {
                        System.err.println("[ERROR] Character might not have been initialized");
                        return false;
                    }
                }
            }).setInputChecker(new InputChecker() {
                public boolean check(String var1, TextCallback var2) {
                    if (System.currentTimeMillis() - PlayerExternalController.this.getState().getController().lastShipSpawn < 5000L) {
                        return false;
                    } else if (EntityRequest.isShipNameValid(var1)) {
                        return true;
                    } else {
                        var2.onFailedTextCheck("Must only contain Letters or numbers or ( _-)!");
                        return false;
                    }
                }
            });
            var1.getInputPanel().onInit();
            var1.getInputPanel().getButtonOK().setText(new Object() {
                public String toString() {
                    PlayerExternalController.this.getState().getController();
                    if (System.currentTimeMillis() - PlayerExternalController.this.getState().getController().lastShipSpawn > 5000L) {
                        return Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_12;
                    } else {
                        PlayerExternalController.this.getState().getController();
                        return Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_35 + "(" + (5 - (int)Math.ceil((double)((float)(System.currentTimeMillis() - PlayerExternalController.this.getState().getController().lastShipSpawn) / 1000.0F))) + ")";
                    }
                }
            });
            var1.activate();
            this.suspend(true);
        }
    }

    public void handleMouseEvent(MouseEvent var1) {
        if (!this.isSuspended() && !this.isHinderedInteraction()) {
            PlayerInteractionControlManager var2;
            if (Keyboard.isKeyDown(KeyboardMappings.ASTRONAUT_ROTATE_BLOCK.getMapping()) && ElementKeyMap.isValidType((var2 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager()).getSelectedTypeWithSub())) {
                int var3 = GUIOrientationSettingElement.getMaxRotation(var2);
                var2.setBlockOrientation(FastMath.cyclicModulo(var2.getBlockOrientation() + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), var3));
            }

            if (!var1.state) {
                if (var1.button == 0) {
                    try {
                        this.checkAddAndRemove(!EngineSettings.C_MOUSE_BUTTON_SWITCH.isOn());
                    } catch (ElementPositionBlockedException var5) {
                        var5.printStackTrace();
                    }
                } else if (var1.button == 1) {
                    try {
                        this.checkAddAndRemove(EngineSettings.C_MOUSE_BUTTON_SWITCH.isOn());
                    } catch (ElementPositionBlockedException var4) {
                        var4.printStackTrace();
                    }
                }
            }
        }

        super.handleMouseEvent(var1);
    }

    public void onSwitch(boolean var1) {
        if (var1) {
            if (this.getState().getCharacter() == null) {
                this.setActive(false);
                return;
            }

            for(SymmetryPlane symmetryPlane : getAllSymmetryPlanes()) {
                symmetryPlane.setEnabled(false);
            }
            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().save(this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().user);
            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().user = this.getState().getCharacter().getUniqueIdentifier();
            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().reset();
            if (this.followerCamera == null) {
                this.followerCamera = new PlayerCamera(this.getState(), this.getState().getCharacter());
            }

            if (((PlayerCamera)this.followerCamera).getCharacter() != this.getState().getCharacter()) {
                ((PlayerCamera)this.followerCamera).setCharacter(this.getState().getCharacter());
            }

            Controller.setCamera(this.followerCamera);
        }

        super.onSwitch(var1);
    }

    public void update(Timer var1) {
        CameraMouseState.setGrabbed(true);
        if (this.selectedBlock != null) {
            if (!this.getState().getCurrentSectorEntities().containsKey(this.selectedBlock.getSegmentController().getId())) {
                this.selectedBlock = null;
            } else {
                this.selectedBlock.refresh();
                if (this.selectedBlock.getType() == 0) {
                    this.selectedBlock = null;
                }
            }
        }

        if (this.toSelectSegController != null) {
            if (this.getState().getCurrentSectorEntities().containsKey(this.toSelectSegController.getId()) && System.currentTimeMillis() - this.toSelectTime <= 8000L) {
                SegmentPiece var2;
                if ((var2 = this.toSelectSegController.getSegmentBuffer().getPointUnsave(this.toSelectBox)) != null && ElementKeyMap.isValidType(var2.getType())) {
                    this.selectedBlock = var2;
                    this.toSelectSegController = null;
                    this.toSelectBox = null;
                }
            } else {
                this.toSelectSegController = null;
                this.toSelectBox = null;
            }
        }

        if (this.controllersDirty) {
            this.controllersDirty = false;
        }

        if (this.getState().getCharacter() == null) {
            System.err.println("[WARNING] state character is removed: entering spawn screen!");
            this.setActive(false);
            this.getState().getGlobalGameControlManager().getIngameControlManager().getAutoRoamController().setActive(true);
        } else {
            if (this.getState().getCharacter().getActivatedStuckProtection() > 0L) {
                long var6;
                if ((var6 = System.currentTimeMillis() - this.getState().getCharacter().getActivatedStuckProtection()) < 8000L) {
                    int var4 = (int)(8L - var6 / 1000L);
                    String var5 = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_20, new Object[]{KeyboardMappings.STUCK_PROTECT.getKeyChar(), var4});
                    if (this.last != null) {
                        this.getState().getController().changePopupMessage(this.last, var5);
                    } else {
                        this.getState().getController().popupInfoTextMessage(var5, 0.0F);
                    }

                    this.last = var5;
                    return;
                }

                if (this.last != null) {
                    this.getState().getController().endPopupMessage(this.last);
                }

                this.getState().getCharacter().setActivatedStuckProtection(0L);
                this.last = null;
            }

        }
    }

    private void buildSpaceStation() {
        PlayerGameTextInput var1;
        (var1 = new PlayerGameTextInput("PlayerExternalController_NEW_ENTITY_NAME", this.getState(), 50, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_21, this.description, this.getState().getPlayerName() + "_" + System.currentTimeMillis()) {
            public String[] getCommandPrefixes() {
                return null;
            }

            public String handleAutoComplete(String var1, TextCallback var2, String var3) {
                return var1;
            }

            public boolean isOccluded() {
                return this.getState().getController().getPlayerInputs().indexOf(this) != this.getState().getController().getPlayerInputs().size() - 1;
            }

            public void onDeactivate() {
                System.err.println("deactivate");
                PlayerExternalController.this.suspend(false);
            }

            public void onFailedTextCheck(String var1) {
                this.setErrorMessage("NAME INVALID: " + var1);
            }

            public boolean onInput(String var1) {
                if (this.getState().getCharacter() != null && this.getState().getCharacter().getPhysicsDataContainer() != null && this.getState().getCharacter().getPhysicsDataContainer().isInitialized()) {
                    Transform var2 = new Transform();
                    Vector3f var3 = new Vector3f(Controller.getCamera().getForward());
                    var2.set(this.getState().getCharacter().getPhysicsDataContainer().getCurrentPhysicsTransform());
                    var3.scale(2.0F);
                    var2.origin.add(var3);
                    var2.basis.rotY(-1.5707964F);

                    try {
                        String var5 = EntityRequest.convertStationEntityName(var1.trim());
                        this.getState().getController().requestNewStation(var2, this.getState().getPlayer(), var5, var1.trim());
                        return true;
                    } catch (Exception var4) {
                        var4.printStackTrace();
                        throw new RuntimeException(var4);
                    }
                } else {
                    return false;
                }
            }
        }).setInputChecker(new InputChecker() {
            public boolean check(String var1, TextCallback var2) {
                if (EntityRequest.isShipNameValid(var1)) {
                    return true;
                } else {
                    var2.onFailedTextCheck(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_22);
                    return false;
                }
            }
        });
        var1.activate();
    }

    private void repairSpaceStation(final SegmentPiece var1, long var2) {
        PlayerGameTextInput var4;
        (var4 = new PlayerGameTextInput("CONFIRM", this.getState(), 50, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_23, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_24, new Object[]{var2}), var1.getSegment().getSegmentController().getRealName()) {
            public String[] getCommandPrefixes() {
                return null;
            }

            public String handleAutoComplete(String var1x, TextCallback var2, String var3) {
                return var1x;
            }

            public boolean isOccluded() {
                return this.getState().getController().getPlayerInputs().indexOf(this) != this.getState().getController().getPlayerInputs().size() - 1;
            }

            public void onDeactivate() {
                System.err.println("deactivate");
                PlayerExternalController.this.suspend(false);
            }

            public void onFailedTextCheck(String var1x) {
                this.setErrorMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_25, new Object[]{var1x}));
            }

            public boolean onInput(String var1x) {
                if (this.getState().getCharacter() != null && this.getState().getCharacter().getPhysicsDataContainer() != null && this.getState().getCharacter().getPhysicsDataContainer().isInitialized()) {
                    this.getState().getPlayer().sendSimpleCommand(SimplePlayerCommands.REPAIR_STATION, new Object[]{var1.getSegment().getSegmentController().getId(), var1x});
                    return true;
                } else {
                    return false;
                }
            }
        }).setInputChecker(new InputChecker() {
            public boolean check(String var1, TextCallback var2) {
                if (EntityRequest.isShipNameValid(var1)) {
                    return true;
                } else {
                    var2.onFailedTextCheck(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_CHARACTER_PLAYEREXTERNALCONTROLLER_26);
                    return false;
                }
            }
        });
        var4.activate();
    }

    private void initialize() {
    }

    public void notifyElementChanged() {
        this.controllersDirty = true;
        this.getState().getWorldDrawer().getBuildModeDrawer().flagControllerSetChanged();
    }

    private void selectCameraBlock() {
        ClosestRayResultCallback var1;
        if ((var1 = this.getNearestIntersection()) != null && var1.hasHit() && var1 instanceof CubeRayCastResult) {
            CubeRayCastResult var3;
            if ((var3 = (CubeRayCastResult)var1).getSegment() != null && var3.getSegment().getSegmentController() instanceof EditableSendableSegmentController) {
                Vector3i var2 = var3.getSegment().getAbsoluteElemPos(var3.getCubePos(), new Vector3i());
                if (this.selectedBlock == null || !this.selectedBlock.getAbsolutePos(new Vector3i()).equals(var2)) {
                    this.selectedBlock = var3.getSegment().getSegmentController().getSegmentBuffer().getPointUnsave(var2);
                    if (this.selectedBlock != null && this.getState().getController().getTutorialMode() != null && this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof ConnectedFromToTestState && !((ConnectedFromToTestState)this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState()).checkConnectBlock(this.selectedBlock, this.getState())) {
                        this.selectedBlock = null;
                    }

                    return;
                }
            }

            this.selectedBlock = null;
        } else {
            this.selectedBlock = null;
        }
    }

    public void sendLastShipName(String var1) {
        this.setChanged();
        this.notifyObservers(var1);
    }

    public void setSelectedBlock(SegmentPiece var1) {
        this.selectedBlock = var1;
    }
}