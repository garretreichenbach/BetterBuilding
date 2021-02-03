package org.schema.game.common.controller;

import com.bulletphysics.collision.dispatch.CollisionWorld.ClosestRayResultCallback;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.SolverConstraint;
import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.GameClientController;
import org.schema.game.client.controller.PlayerGameOkCancelInput;
import org.schema.game.client.controller.manager.ingame.BuildCallback;
import org.schema.game.client.controller.manager.ingame.BuildInstruction;
import org.schema.game.client.controller.manager.ingame.BuildRemoveCallback;
import org.schema.game.client.controller.manager.ingame.BuildSelectionCallback;
import org.schema.game.client.controller.manager.ingame.SymmetryPlanes;
import org.schema.game.client.controller.manager.ingame.BuildInstruction.Add;
import org.schema.game.client.controller.manager.ingame.BuildInstruction.Remove;
import org.schema.game.client.controller.manager.ingame.BuildInstruction.Replace;
import org.schema.game.client.controller.tutorial.states.PlaceElementTestState;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.data.PlayerControllable;
import org.schema.game.client.view.buildhelper.BuildHelper;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.client.view.gui.buildtools.BuildToolsPanel;
import org.schema.game.common.controller.damage.DamageDealerType;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.damage.HitType;
import org.schema.game.common.controller.damage.acid.AcidDamageManager;
import org.schema.game.common.controller.damage.beam.DamageBeamHittable;
import org.schema.game.common.controller.damage.projectile.ProjectileController;
import org.schema.game.common.controller.damage.projectile.ProjectileHittable;
import org.schema.game.common.controller.elements.BeamState;
import org.schema.game.common.controller.elements.ExplosiveManagerContainerInterface;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.ParticleHandler;
import org.schema.game.common.controller.elements.PulseController;
import org.schema.game.common.controller.elements.StationaryManagerContainer;
import org.schema.game.common.controller.elements.beam.repair.RepairBeamHandler;
import org.schema.game.common.controller.elements.power.PowerManagerInterface;
import org.schema.game.common.controller.elements.warpgate.WarpgateCollectionManager;
import org.schema.game.common.controller.generator.EmptyCreatorThread;
import org.schema.game.common.controller.rails.DockingFailReason;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.element.BlockOrientation;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.element.ElementInformation.ResourceInjectionType;
import org.schema.game.common.data.explosion.AfterExplosionCallback;
import org.schema.game.common.data.explosion.ExplosionData;
import org.schema.game.common.data.explosion.ExplosionRunnable;
import org.schema.game.common.data.fleet.Fleet;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.Faction;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventoryHolder;
import org.schema.game.common.data.world.RemoteSector;
import org.schema.game.common.data.world.RemoteSegment;
import org.schema.game.common.data.world.Sector;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SegmentData;
import org.schema.game.common.data.world.SegmentDataWriteException;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.common.data.world.StellarSystem;
import org.schema.game.network.objects.NetworkSegmentController;
import org.schema.game.network.objects.remote.RemoteSegmentPiece;
import org.schema.game.server.data.FactionState;
import org.schema.game.server.data.GameServerState;
import org.schema.game.server.data.ServerConfig;
import org.schema.game.server.data.simulation.npc.diplomacy.DiplomacyAction.DiplActionType;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.network.StateInterface;
import org.schema.schine.network.objects.Sendable;
import org.schema.schine.network.server.ServerMessage;

/**
 * EditableSendableSegmentController.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public abstract class EditableSendableSegmentController extends SendableSegmentController implements BuilderInterface, Salvage, DamageBeamHittable, ProjectileHittable, ParticleHandler {
    private static final long MIN_TIME_BETWEEN_EDITS = 50L;
    private final Vector3i absPosCache = new Vector3i();
    private boolean flagCharacterExitCheckByExplosion;
    private Object flagCoreDestroyedByExplosion;
    private Vector3f tmpPosA = new Vector3f();
    private Vector3f tmpPosB = new Vector3f();
    private Vector3i local = new Vector3i();
    public final EditableSendableSegmentController.DryTestBuild dryBuildTest = new EditableSendableSegmentController.DryTestBuild();
    private Damager lastSalvaged = null;
    private final AcidDamageManager acidDamageManagerServer;
    int uNumMagnetDock = 0;

    public EditableSendableSegmentController(StateInterface var1) {
        super(var1);
        if (this.isOnServer()) {
            this.acidDamageManagerServer = new AcidDamageManager(this);
        } else {
            this.acidDamageManagerServer = null;
        }
    }

    public PulseController getPulseController() {
        return !this.isOnServer() ? ((GameClientState)this.getState()).getPulseController() : ((GameServerState)this.getState()).getUniverse().getSector(this.getSectorId()).getPulseController();
    }

    protected void onSalvaged(Damager var1) {
        this.lastSalvaged = var1;
    }

    public boolean allowedToEdit(PlayerState var1) {
        if (this.railController.isDockedAndExecuted() && this.railController.getRoot() instanceof ShopSpaceStation) {
            return this.lastDockerPlayerServerLowerCase.length() == 0 || this.lastDockerPlayerServerLowerCase.equals(var1.getName().toLowerCase(Locale.ENGLISH));
        } else if (this.getFactionId() != 0 && ((FactionState)var1.getState()).getFactionManager().existsFaction(this.getFactionId())) {
            if (this.getFactionId() == var1.getFactionId() && this.isSufficientFactionRights(var1)) {
                return true;
            } else if (this.getFactionId() == var1.getFactionId() && this.isOwnerSpecific(var1)) {
                return true;
            } else {
                return this.getFactionId() == 0 || ((FactionState)var1.getState()).getFactionManager().existsFaction(this.getFactionId()) && this.getFactionId() == var1.getFactionId() && this.isSufficientFactionRights(var1);
            }
        } else {
            return true;
        }
    }

    public void setWrittenForUnload(boolean var1) {
        this.hadAtLeastOneElement = false;
        super.setWrittenForUnload(var1);
    }

    public void onWrite() {
        this.hadAtLeastOneElement = false;
    }

    public void getNearestIntersectingElementPosition(Vector3f var1, Vector3f var2, Vector3i var3, float var4, BuildRemoveCallback var5, SymmetryPlane var6, short var7, short var8, BuildHelper var9, BuildInstruction var10, Set<Segment> var11) {
        if (System.currentTimeMillis() - this.lastEditBlocks >= 50L) {
            Vector3i var12 = new Vector3i();
            SegmentPiece var19;
            if ((var19 = this.getNearestPiece(var1, var2, var4, var12, var3)) == null) {
                System.err.println("[SEGCONTROLLER][ELEMENT][REMOVE] NO NEAREST PIECE FOUND");
            } else {
                System.err.println("[SEGCONTROLLER][ELEMENT][REMOVE] PICKING UP: " + var19.toString() + "; orientation: " + var19.getOrientation() + "; " + Element.getSideString(var19.getOrientation()));
                boolean var22 = var3.equals(-1, -1, -1);
                Vector3i var20;
                int var23 = Math.min((var20 = var19.getAbsolutePos(new Vector3i())).x, var20.x + var12.x);
                int var13 = Math.min(var20.y, var20.y + var12.y);
                int var14 = Math.min(var20.z, var20.z + var12.z);
                int var15 = Math.max(var20.x, var20.x + var12.x);
                int var16 = Math.max(var20.y, var20.y + var12.y);
                int var24 = Math.max(var20.z, var20.z + var12.z);
                if (var15 == var20.x) {
                    ++var23;
                    ++var15;
                }

                if (var16 == var20.y) {
                    ++var13;
                    ++var16;
                }

                if (var24 == var20.z) {
                    ++var14;
                    ++var24;
                }

                if (var6.inPlaceMode()) {
                    var6.setPlaceMode(false);
                } else {
                    byte var21 = (byte)((GameClientState)this.getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation();
                    System.currentTimeMillis();

                    while(var14 < var24) {
                        for(int var17 = var13; var17 < var16; ++var17) {
                            for(int var18 = var23; var18 < var15; ++var18) {
                                this.remove(var18, var17, var14, var5, var22, var11, var7, var8, var21, var9, var10);
                                this.removeInSymmetry(var18, var17, var14, var5, var22, var11, var7, var8, var21, var9, var10, var6);
                            }
                        }

                        ++var14;
                    }

                    if (!var22) {
                        boolean var25 = false;
                        System.err.println("[CLIENT][REMOVEBLOCKS] multiRem: UPDATING AABBS: " + var3);
                        Iterator var26 = var11.iterator();

                        while(var26.hasNext()) {
                            Segment var27;
                            if (!(var27 = (Segment)var26.next()).isEmpty()) {
                                var27.getSegmentController().getSegmentProvider().enqueueAABBChange(var27);
                            } else {
                                var25 = true;
                            }
                        }

                        if (var25) {
                            this.getSegmentBuffer().restructBB();
                        }
                    }

                }
            }
        }
    }

    public int getNearestIntersection(short var1, Vector3f var2, Vector3f var3, BuildCallback var4, int var5, boolean var6, DimensionFilter var7, Vector3i var8, int var9, float var10, SymmetryPlane var11, BuildHelper var12, BuildInstruction var13) throws ElementPositionBlockedException, BlockedByDockedElementException, BlockNotBuildTooFast {
        if ((var9 = this.checkAllPlace(var1, var9, var11)) < 0) {
            return 0;
        } else if (!var11.inPlaceMode() && !this.allowedType(var1)) {
            System.err.println("Type is not allowed on " + this + "; " + var1);
            return 0;
        } else if (System.currentTimeMillis() - this.lastEditBlocks < 50L) {
            return 0;
        } else {
            Vector3i var14 = new Vector3i();
            SegmentPiece var15 = null;
            Vector3i var16 = new Vector3i();

            try {
                var15 = this.getNextToNearestPiece(var2, var3, var16, var10, var8, var14);
                if (var11.inPlaceMode() && var15 != null) {
                    Vector3i var22 = var15.getAbsolutePos(new Vector3i());
                    switch(var11.getMode()) {
                        case XY:
                            System.err.println("SYM XY PLANE SET");
                            var11.getPlane().z = var22.z;
                            var11.setPlaceMode(false);
                            var11.setEnabled(true);
                            break;
                        case XZ:
                            System.err.println("SYM XZ PLANE SET");
                            var11.getPlane().y = var22.y;
                            var11.setPlaceMode(false);
                            var11.setEnabled(true);
                            break;
                        case YZ:
                            System.err.println("SYM YZ PLANE SET");
                            var11.getPlane().x = var22.x;
                            var11.setPlaceMode(false);
                            var11.setEnabled(true);
                            break;
                    }

                    var11.setPlaceMode(false);
                    return 0;
                }

                System.err.println("[CLIENT][EDIT] PLACING AT " + var15 + "; size: " + var8 + " --> " + var14 + "; orient " + var5 + "(" + Element.getSideString(var5) + ") -map-> " + var5 + " PHY: " + (var15 != null ? var15.getSegment().getSegmentController().getPhysicsDataContainer().getObject() : ""));
            } catch (CannotImmediateRequestOnClientException var20) {
                System.err.println("[CLIENT][WARNING] Cannot ADD! segment not yet in buffer " + var20.getSegIndex() + ". -> requested");
                return 0;
            }

            if (var15 != null) {
                if (var7 != null && !var7.isValid(var15.getAbsolutePos(new Vector3i()))) {
                    return 0;
                } else {
                    if (var15.getSegment().isEmpty()) {
                        SegmentData var10000 = this.getSegmentProvider().getFreeSegmentData();
                        var2 = null;
                        var10000.assignData(var15.getSegment());
                    }

                    System.err.println("[CLIENT][EDIT] adding new element to " + this.getClass().getSimpleName() + " at " + var15 + ", type " + var1);
                    int[] var21 = new int[2];
                    Vector3i var23 = var15.getAbsolutePos(new Vector3i());
                    var21[1] = var9;
                    int var25 = var14.x < 0 ? var23.x + var14.x + 1 : var23.x;
                    int var26 = var14.y < 0 ? var23.y + var14.y + 1 : var23.y;
                    var9 = var14.z < 0 ? var23.z + var14.z + 1 : var23.z;
                    int var27 = var14.x < 0 ? var23.x + 1 : var23.x + var14.x;
                    int var29 = var14.y < 0 ? var23.y + 1 : var23.y + var14.y;
                    int var24 = var14.z < 0 ? var23.z + 1 : var23.z + var14.z;

                    int var17;
                    int var18;
                    int var28;
                    try {
                        for(var28 = var9; var28 < var24 && var21[1] > 0; ++var28) {
                            for(var17 = var26; var17 < var29 && var21[1] > 0; ++var17) {
                                for(var18 = var25; var18 < var27 && var21[1] > 0; ++var18) {
                                    this.dryBuildTest.build(var18, var17, var28, var1, var5, var6, var4, var16, var21, var12, var13);
                                    this.buildInSymmetry(var18, var17, var28, var1, var5, var6, var4, var16, var21, var13, var12, var11, this.dryBuildTest);
                                }
                            }
                        }
                    } catch (PositionBlockedException var19) {
                        if (!this.isOnServer()) {
                            ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_15, 0.0F);
                        }

                        return 0;
                    }

                    for(var28 = var9; var28 < var24 && var21[1] > 0; ++var28) {
                        for(var17 = var26; var17 < var29 && var21[1] > 0; ++var17) {
                            for(var18 = var25; var18 < var27 && var21[1] > 0; ++var18) {
                                this.build(var18, var17, var28, var1, var5, var6, var4, var16, var21, var12, var13);
                                this.buildInSymmetry(var18, var17, var28, var1, var5, var6, var4, var16, var21, var13, var12, var11, this);
                            }
                        }
                    }

                    return var21[0];
                }
            } else {
                System.err.println("no intersection found in world currentSegmentContext");
                return 0;
            }
        }
    }

    public void startCreatorThread() {
        if (this.getCreatorThread() == null) {
            this.setCreatorThread(new EmptyCreatorThread(this));
        }

    }

    public boolean isEmptyOnServer() {
        return this.hadAtLeastOneElement && this.getTotalElements() == 0;
    }

    public boolean allowedType(short var1) {
        if (!ElementKeyMap.getInfo(var1).isPlacable()) {
            if (!this.isOnServer()) {
                if (1 == var1) {
                    ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_0, 0.0F);
                } else {
                    ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_1, 0.0F);
                }
            }

            return false;
        } else {
            return true;
        }
    }

    public void build(final int var1, final int var2, final int var3, short var4, int var5, boolean var6, BuildSelectionCallback var7, Vector3i var8, int[] var9, BuildHelper var10, BuildInstruction var11) {
        if (var10 == null || var10.contains(var1, var2, var3)) {
            if (ElementKeyMap.getInfo(var4).resourceInjection != ResourceInjectionType.OFF) {
                var5 = 0;
            }

            if (var9[1] > 0) {
                int var10002;
                SegmentPiece var20;
                if ((var20 = this.getSegmentBuffer().getPointUnsave(var1, var2, var3)) != null) {
                    SegmentCollisionCheckerCallback var22 = new SegmentCollisionCheckerCallback();
                    if (this.getCollisionChecker().checkPieceCollision(var20, var22, true)) {
                        System.err.println(this.getState() + "; " + this + " Block at " + var20 + " blocked");
                        if (!this.isOnServer()) {
                            ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_18, 0.0F);
                        }

                        return;
                    }

                    short var23 = 0;
                    if (var4 > 0) {
                        if (BuildToolsPanel.blueprintPlacementSetting > 0) {
                            var23 = (short)BuildToolsPanel.blueprintPlacementSetting;
                        } else {
                            var23 = 127;
                        }
                    }

                    short var24 = var4;
                    if (ElementKeyMap.isValidType(var4) && ElementKeyMap.getInfoFast(var4).isReactorChamberGeneral()) {
                        Vector3i var14 = new Vector3i();
                        SegmentPiece var15 = new SegmentPiece();

                        for(int var16 = 0; var16 < 6; ++var16) {
                            var14.set(var1, var2, var3);
                            var14.add(Element.DIRECTIONSi[var16]);
                            SegmentPiece var17;
                            if ((var17 = this.getSegmentBuffer().getPointUnsave(var14, var15)) != null && ElementKeyMap.isValidType(var17.getType()) && ElementKeyMap.getInfo(var17.getType()).chamberRoot == var4) {
                                var24 = var17.getType();
                                break;
                            }
                        }
                    }

                    if (var20.getSegment().addElement(var24, var20.getPos(this.tmpLocalPos), var5, var6, var23, this)) {
                        if (var11 != null) {
                            var11.recordAdd(var4, ElementCollection.getIndex(var1, var2, var3), var5, var6, var7 != null ? var7.getSelectedControllerPos() : null);
                        }

                        this.lastEditBlocks = System.currentTimeMillis();
                        ((RemoteSegment)var20.getSegment()).setLastChanged(System.currentTimeMillis());
                        var20.refresh();
                        Vector3i var25 = var20.getAbsolutePos(new Vector3i());
                        long var26 = var20.getAbsoluteIndex();
                        if (var7 != null && var7 instanceof BuildCallback) {
                            ((BuildCallback)var7).onBuild(var25, var8, var4);
                        }

                        RemoteSegmentPiece var18;
                        label116: {
                            var18 = new RemoteSegmentPiece(var20, this.getNetworkObject());
                            if (var7 != null && var7.getSelectedControllerPos() != -9223372036854775808L && var7.getSelectedControllerPos() != var26) {
                                SegmentPiece var19;
                                if ((var19 = this.getSegmentBuffer().getPointUnsave(var7.getSelectedControllerPos())) == null) {
                                    System.err.println("[CLIENT] ERROR: piece not loaded: " + var7.getSelectedControllerPos());
                                    break label116;
                                }

                                if (!(this instanceof ManagedSegmentController) || ((ManagedSegmentController)this).getManagerContainer().canBeControlled(var19.getType(), var4)) {
                                    if (!ElementKeyMap.isValidType(var19.getType()) || !ElementKeyMap.getInfo(var19.getType()).controlsAll() && !ElementKeyMap.getInfo(var4).getControlledBy().contains(var19.getType()) && !ElementInformation.canBeControlled(var19.getType(), var4)) {
                                        var18.controllerPos = -9223372036854775808L;
                                    } else {
                                        var18.controllerPos = var7.getSelectedControllerPos();
                                    }
                                    break label116;
                                }
                            }

                            var18.controllerPos = -9223372036854775808L;
                        }

                        this.sendBlockMod(var18);
                        var10002 = var9[0]++;
                        var10002 = var9[1]--;
                    }

                    if (!var20.getSegment().isEmpty() && this.getSegmentBuffer().getSegmentState(var20.getAbsolutePos(new Vector3i())) < 0) {
                        this.getSegmentBuffer().addImmediate(var20.getSegment());
                    }

                    return;
                }

                this.lastEditBlocks = System.currentTimeMillis();
                var20 = new SegmentPiece();
                byte var12 = !var6 ? 0 : ElementInformation.defaultActive(var4);
                var20.setActive(var12 != 0);
                var20.setType(var4);
                var20.setOrientation((byte)var5);
                var20.setHitpointsByte(127);
                if (var11 != null) {
                    var11.recordAdd(var4, ElementCollection.getIndex(var1, var2, var3), var5, var6, var7.getSelectedControllerPos());
                }

                RemoteSegmentPiece var21;
                label151: {
                    var21 = new RemoteSegmentPiece(var20, this.getNetworkObject()) {
                        public int toByteStream(DataOutputStream var1x) throws IOException {
                            assert this.get() != null;

                            this.writeDynamicPosition(var1, var2, var3, true, var1x);
                            SegmentPiece.serializeData(var1x, ((SegmentPiece)this.get()).getData());
                            return 1;
                        }
                    };
                    if (var7.getSelectedControllerPos() != -9223372036854775808L) {
                        if (var7.getSelectedControllerPos() == ElementCollection.getIndex(var1, var2, var3)) {
                            System.err.println("[CLIENT] WARNING2: not sending controller equals block to build: " + var1 + "," + var2 + "," + var3);
                        } else {
                            SegmentPiece var13;
                            if ((var13 = this.getSegmentBuffer().getPointUnsave(var7.getSelectedControllerPos())) == null) {
                                System.err.println("[CLIENT] not loaded piece: " + var13);
                                break label151;
                            }

                            if (!(this instanceof ManagedSegmentController) || ((ManagedSegmentController)this).getManagerContainer().canBeControlled(var13.getType(), var4)) {
                                if (var13.getType() > 0 && ElementKeyMap.getInfo(var4).getControlledBy().contains(var13.getType())) {
                                    var21.controllerPos = var7.getSelectedControllerPos();
                                    break label151;
                                }

                                System.err.println("[CLIENT] WARNING1: not sending controller: controller type cannot control this: " + var1 + ", " + var2 + ", " + var3 + " tryed to connect to " + var13);
                            }
                        }
                    }

                    var21.controllerPos = -9223372036854775808L;
                }

                this.sendBlockMod(var21);
                var10002 = var9[0]++;
                var10002 = var9[1]--;
            }

        }
    }

    public boolean canAttack(Damager var1) {
        if (!this.isHomeBase() && !this.isHomeBaseFor(this.getFactionId())) {
            return true;
        } else {
            if (var1 != null && var1 instanceof PlayerControllable) {
                List var4 = ((PlayerControllable)var1).getAttachedPlayers();

                for(int var2 = 0; var2 < var4.size(); ++var2) {
                    PlayerState var3 = (PlayerState)var4.get(var2);
                    if (System.currentTimeMillis() - var3.lastSectorProtectedMsgSent > 5000L) {
                        var3.lastSectorProtectedMsgSent = System.currentTimeMillis();
                        var3.sendServerMessage(new ServerMessage(new Object[]{25}, 2, var3.getId()));
                    }
                }
            }

            return false;
        }
    }

    private void checkCharacterExit() {
        System.err.println("[SegController] CHECKING CHARACTER EXIT");
        if (this instanceof PlayerControllable) {
            Iterator var1 = ((PlayerControllable)this).getAttachedPlayers().iterator();

            while(var1.hasNext()) {
                ((PlayerState)var1.next()).getControllerState().checkPlayerControllers();
            }
        }

    }

    public boolean checkCore(SegmentPiece var1) {
        return true;
    }

    public float damageElement(short var1, int var2, SegmentData var3, int var4, Damager var5, DamageDealerType var6, long var7) {
        if (!ElementKeyMap.exists(var1)) {
            return 0.0F;
        } else {
            ElementInformation var9 = ElementKeyMap.getInfoFast(var1);
            short var10 = var3.getHitpointsByte(var2);
            int var11 = ElementKeyMap.convertToFullHP(var1, var10);
            int var12 = Math.max(0, var11 - var4);
            float var18 = (float)(var11 - var12);
            if (!this.isOnServer()) {
                return var18;
            } else {
                short var13 = ElementKeyMap.convertToByteHP(var1, var12);

                assert var13 <= 127 : "FULL: " + var13 + "; " + ElementKeyMap.getInfo(var1).getMaxHitPointsFull();

                if (var13 != var10) {
                    try {
                        var3.setHitpointsByte(var2, var13);
                    } catch (SegmentDataWriteException var17) {
                        assert var3 == var3.getSegment().getSegmentData() : var3 + "; " + var3.getSegment().getSegmentData();

                        var3 = SegmentDataWriteException.replaceData(var3.getSegment());

                        try {
                            var3.setHitpointsByte(var2, ElementKeyMap.convertToByteHP(var1, var12));
                        } catch (SegmentDataWriteException var16) {
                            var16.printStackTrace();
                            throw new RuntimeException(var16);
                        }
                    }

                    if (var12 <= 0) {
                        this.getHpController().onElementDestroyed(var5, var9, var6, var7);
                        if (this.isEnterable(var1) && var3.getSegment() != null) {
                            this.forceCharacterExit(new SegmentPiece(var3.getSegment(), var2));
                        }

                        if (var1 == this.getCoreType() && var3.getSegment().getAbsoluteIndex(var2) == ElementCollection.getIndex(Ship.core)) {
                            try {
                                var3.setHitpointsByte(var2, 0);
                            } catch (SegmentDataWriteException var15) {
                                var3 = SegmentDataWriteException.replaceData(var3.getSegment());

                                try {
                                    var3.setHitpointsByte(var2, 0);
                                } catch (SegmentDataWriteException var14) {
                                    var14.printStackTrace();
                                    throw new RuntimeException(var14);
                                }
                            }

                            this.onCoreDestroyed(var5);
                            this.onCoreHitAlreadyDestroyed((float)var4);
                        } else {
                            var3.getSegment().removeElement(var2, false);
                            this.getSegmentProvider().enqueueAABBChange(var3.getSegment());
                        }

                        if (ServerConfig.ENABLE_BREAK_OFF.isOn()) {
                            var3.getSegment().getAbsoluteElemPos(var2, this.absPosCache);
                            this.checkBreak(this.absPosCache);
                        }
                    }
                }

                return var18;
            }
        }
    }

    public void doDimExtensionIfNecessary(Segment var1, byte var2, byte var3, byte var4) {
        if (var2 == 0) {
            this.extendDim(0, var1.absPos.x - 1, -1, 0, 0);
        }

        if (var3 == 0) {
            this.extendDim(1, var1.absPos.y - 1, 0, -1, 0);
        }

        if (var4 == 0) {
            this.extendDim(2, var1.absPos.z - 1, 0, 0, -1);
        }

        if (var2 == 31) {
            this.extendDim(0, var1.absPos.x + 1, 1, 0, 0);
        }

        if (var3 == 31) {
            this.extendDim(1, var1.absPos.y + 1, 0, 1, 0);
        }

        if (var4 == 31) {
            this.extendDim(2, var1.absPos.z + 1, 0, 0, 1);
        }

    }

    public void extendDim(int var1, int var2, int var3, int var4, int var5) {
        if (!this.isInboundCoord(var1, var2)) {
            Vector3iSegment var10000 = this.getMaxPos();
            var10000.x += var3 > 0 ? var3 : 0;
            var10000 = this.getMaxPos();
            var10000.y += var4 > 0 ? var4 : 0;
            var10000 = this.getMaxPos();
            var10000.z += var5 > 0 ? var5 : 0;
            var10000 = this.getMinPos();
            var10000.x += var3 < 0 ? var3 : 0;
            var10000 = this.getMinPos();
            var10000.y += var4 < 0 ? var4 : 0;
            var10000 = this.getMinPos();
            var10000.z += var5 < 0 ? var5 : 0;
            this.setChangedForDb(true);
        }

    }

    public void forceAllCharacterExit() {
        if (this instanceof PlayerControllable) {
            Iterator var1 = ((PlayerControllable)this).getAttachedPlayers().iterator();

            while(var1.hasNext()) {
                ((PlayerState)var1.next()).getControllerState().forcePlayerOutOfSegmentControllers();
            }
        }

    }

    public void forceCharacterExit(SegmentPiece var1) {
        if (var1.getType() != 1) {
            synchronized(this.getState().getLocalAndRemoteObjectContainer().getLocalObjects()) {
                Iterator var3 = this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().values().iterator();

                while(var3.hasNext()) {
                    Sendable var4;
                    if ((var4 = (Sendable)var3.next()) instanceof PlayerState) {
                        ((PlayerState)var4).onDestroyedElement(var1);
                    }
                }

            }
        }
    }

    public ProjectileController getParticleController() {
        return !this.isOnServer() ? ((GameClientState)this.getState()).getParticleController() : ((GameServerState)this.getState()).getUniverse().getSector(this.getSectorId()).getParticleController();
    }

    protected short getCoreType() {
        return 1;
    }

    public Object getFlagCoreDestroyedByExplosion() {
        return this.flagCoreDestroyedByExplosion;
    }

    public void setFlagCoreDestroyedByExplosion(Object var1) {
        this.flagCoreDestroyedByExplosion = var1;
    }

    public int checkPlace(short var1, short var2, int var3, SymmetryPlane var4) {
        if ((var4 == null || !var4.inPlaceMode()) && var1 == var2) {
            if (this.getElementClassCountMap().get(var2) > 0) {
                if (!this.isOnServer()) {
                    ((GameClientState)this.getState()).getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_3, new Object[]{ElementKeyMap.toString(var2)}), 0.0F);
                }

                return -1;
            } else {
                return 1;
            }
        } else {
            return var3;
        }
    }

    public int checkAllPlace(short var1, int var2, SymmetryPlane var3) {
        if ((var2 = this.checkPlace(var1, (short)291, var2, var3)) < 0) {
            return -1;
        } else if ((var2 = this.checkPlace(var1, (short)121, var2, var3)) < 0) {
            return -1;
        } else if ((var2 = this.checkPlace(var1, (short)347, var2, var3)) < 0) {
            return -1;
        } else {
            return (var2 = this.checkPlace(var1, (short)654, var2, var3)) < 0 ? -1 : var2;
        }
    }

    public void buildInSymmetry(int var1, int var2, int var3, short var4, int var5, boolean var6, BuildCallback var7, Vector3i var8, int[] var9, BuildInstruction var10, BuildHelper var11, SymmetryPlane var12, BuilderInterface var13) {
        int var15;
        if(var12.isEnabled()) {
            switch(var12.getMode()) {
                case XY:
                    var15 = (var12.getPlane().z - var3 << 1) + var12.getExtraDist();
                    var13.build(var1, var2, var3 + var15, var4, var12.getMirrorOrientation(var4, var5), var6, var7, var8, var9, var11, var10);
                    break;
                case XZ:
                    var15 = (var12.getPlane().y - var3 << 1) + var12.getExtraDist();
                    var13.build(var1, var2, var3 + var15, var4, var12.getMirrorOrientation(var4, var5), var6, var7, var8, var9, var11, var10);
                    break;
                case YZ:
                    var15 = (var12.getPlane().x - var3 << 1) + var12.getExtraDist();
                    var13.build(var1, var2, var3 + var15, var4, var12.getMirrorOrientation(var4, var5), var6, var7, var8, var9, var11, var10);
                    break;
            }
        }
    }

    public SegmentPiece getNearestPiece(Vector3f var1, Vector3f var2, float var3, Vector3i var4, Vector3i var5) {
        Vector3f var6 = new Vector3f();
        var2.scale(var3);
        var6.add(var1, var2);
        CubeRayCastResult var7;
        (var7 = new CubeRayCastResult(var1, var6, false, new SegmentController[]{this})).setOnlyCubeMeshes(true);
        var7.setIgnoereNotPhysical(true);
        System.err.println("NEAREST: " + var1 + " -> " + var6 + "; DIR: " + var2 + "; scale: " + var3);
        ClosestRayResultCallback var8;
        if ((var8 = this.getPhysics().testRayCollisionPoint(var1, var6, var7, false)).hasHit() && var8.collisionObject != null && var8 instanceof CubeRayCastResult && ((CubeRayCastResult)var8).getSegment() != null) {
            CubeRayCastResult var9;
            (var9 = (CubeRayCastResult)var8).getSegment().getSegmentData().getSegmentController();
            Segment var10 = var9.getSegment();
            Vector3i var11;
            Vector3i var10000 = var11 = new Vector3i(var9.getSegment().pos.x, var9.getSegment().pos.y, var9.getSegment().pos.z);
            var10000.x += var9.getCubePos().x - 16;
            var11.y += var9.getCubePos().y - 16;
            var11.z += var9.getCubePos().z - 16;
            this.getWorldTransformInverse().transform(var9.hitPointWorld);
            IntOpenHashSet var12 = new IntOpenHashSet();
            SegmentPiece var14 = var9.getSegment().getSegmentController().getSegmentBuffer().getPointUnsave(new Vector3i(var11.x + 16, var11.y + 16, var11.z + 16));
            int var13 = Element.getSide(var9.hitPointWorld, var14 == null ? null : var14.getAlgorithm(), var11, var14 != null ? var14.getType() : 0, var14 != null ? var14.getOrientation() : 0, var12);
            System.err.println("[GETNEAREST] SIDE: " + Element.getSideString(var13) + "(" + var13 + "): " + var9.hitPointWorld + "; " + var11);
            var5.x = -var5.x;
            var5.y = -var5.y;
            var5.z = -var5.z;
            switch(var13) {
                case 0:
                    var4.set(var5.x, var5.y, var5.z);
                    break;
                case 1:
                    var4.set(var5.x, var5.y, -var5.z);
                    break;
                case 2:
                    var4.set(var5.x, var5.y, var5.z);
                    break;
                case 3:
                    var4.set(var5.x, -var5.y, var5.z);
                    break;
                case 4:
                    var4.set(var5.x, var5.y, var5.z);
                    break;
                case 5:
                    var4.set(-var5.x, var5.y, var5.z);
                    break;
                default:
                    System.err.println("[BUILDMODEDRAWER] WARNING: NO SIDE recognized!!!");
            }

            return new SegmentPiece(var10, var9.getCubePos());
        } else {
            return null;
        }
    }

    public NetworkSegmentController getNetworkObject() {
        return super.getNetworkObject();
    }

    public void updateLocal(Timer var1) {
        this.getState().getDebugTimer().start(this, "EditableSegmentController");
        if (this.getTotalElements() > 0) {
            this.hadAtLeastOneElement = true;
        }

        if (this.isMarkedForDeleteVolatile()) {
            System.err.println("[EditableSegmentControleler] " + this + " MARKED TO DELETE ON " + this.getState());
        }

        if (this.isOnServer()) {
            this.acidDamageManagerServer.update(var1);
        }

        if (this.lastSalvaged != null) {
            if (this.isOnServer()) {
                try {
                    StellarSystem var2;
                    if (FactionManager.isNPCFaction((var2 = ((GameServerState)this.getState()).getUniverse().getStellarSystemFromSecPos(this.getSector(new Vector3i()))).getOwnerFaction())) {
                        long var3;
                        if (this.lastSalvaged.getOwnerState() != null && this.lastSalvaged.getOwnerState() instanceof PlayerState) {
                            var3 = ((PlayerState)this.lastSalvaged.getOwnerState()).getDbId();
                        } else {
                            var3 = (long)this.lastSalvaged.getFactionId();
                        }

                        if (var3 != 0L && (long)var2.getOwnerFaction() != var3) {
                            ((FactionState)this.getState()).getFactionManager().diplomacyAction(DiplActionType.MINING, var2.getOwnerFaction(), var3);
                        }
                    }
                } catch (IOException var5) {
                    var5.printStackTrace();
                }
            }

            this.lastSalvaged = null;
        }

        if (this.getFlagCoreDestroyedByExplosion() != null) {
            System.err.println("[EditSegController] " + this + " CORE HAS BEEN DESTROYED BY " + this.getFlagCoreDestroyedByExplosion());
            if (this.getFlagCoreDestroyedByExplosion() instanceof Sendable) {
                this.onCoreDestroyed((Damager)this.getFlagCoreDestroyedByExplosion());
            } else {
                this.onCoreDestroyed((Damager)null);
            }

            this.setFlagCoreDestroyedByExplosion((Object)null);
        }

        if (this.isFlagCharacterExitCheckByExplosion()) {
            this.checkCharacterExit();
            this.setFlagCharacterExitCheckByExplosion(false);
        }

        super.updateLocal(var1);
        this.getState().getDebugTimer().end(this, "EditableSegmentController");
    }

    public void addExplosion(Damager var1, DamageDealerType var2, HitType var3, long var4, Transform var6, float var7, float var8, boolean var9, AfterExplosionCallback var10, int var11) {
        this.sendExplosionGraphic(var6.origin);
        ExplosionData var12;
        (var12 = new ExplosionData()).damageType = DamageDealerType.EXPLOSIVE;
        var12.centerOfExplosion = new Transform(var6);
        var12.fromPos = new Vector3f(var6.origin);
        var12.toPos = new Vector3f(var6.origin);
        var12.radius = var7;
        var12.damageInitial = var8;
        var12.damageBeforeShields = 0.0F;
        var12.sectorId = this.getSectorId();
        var12.hitsFromSelf = (var11 & 1) == 1;
        var12.from = var1;
        var12.weaponId = -9223372036854775808L;
        var12.ignoreShieldsSelf = (var11 & 1) == 1;
        var12.ignoreShields = (var11 & 2) == 2;
        var12.chain = var9;
        var12.attackEffectSet = var1.getAttackEffectSet(var4, var2);

        assert var12.attackEffectSet != null;

        var12.hitType = var3;
        var12.afterExplosionHook = var10;
        Sector var13;
        if ((var13 = ((GameServerState)this.getState()).getUniverse().getSector(this.getSectorId())) != null) {
            ExplosionRunnable var14 = new ExplosionRunnable(var12, var13);
            ((GameServerState)this.getState()).enqueueExplosion(var14);
        }

    }

    public void newNetworkObject() {
        this.setNetworkObject(new NetworkSegmentController(this.getState(), this));
    }

    public SegmentPiece getNextToNearestPiece(Vector3f var1, Vector3f var2, Vector3i var3, float var4, Vector3i var5, Vector3i var6) throws ElementPositionBlockedException, BlockNotBuildTooFast {
        CubeRayCastResult var10;
        if ((var10 = ((GameClientState)this.getState()).getWorldDrawer().getBuildModeDrawer().testRayCollisionPoint) != null && var10.hasHit() && var10 instanceof CubeRayCastResult) {
            var2 = new Vector3f(var10.hitPointWorld);
            if ((var10 = (CubeRayCastResult)var10).getSegment() == null) {
                System.err.println("CUBERESULT SEGMENT NULL");
                return null;
            } else {
                Vector3i var16 = new Vector3i(var10.getSegment().pos.x, var10.getSegment().pos.y, var10.getSegment().pos.z);
                var3.set(var10.getSegment().pos.x + var10.getCubePos().x, var10.getSegment().pos.y + var10.getCubePos().y, var10.getSegment().pos.z + var10.getCubePos().z);
                var16.x += var10.getCubePos().x - 16;
                var16.y += var10.getCubePos().y - 16;
                var16.z += var10.getCubePos().z - 16;
                if (((GameClientState)this.getState()).getCurrentSectorId() == this.getSectorId()) {
                    this.getWorldTransformInverse().transform(var2);
                } else {
                    Transform var13;
                    (var13 = new Transform(this.getWorldTransformOnClient())).inverse();
                    var13.transform(var2);
                }

                IntOpenHashSet var14 = new IntOpenHashSet();

                SegmentPiece var18;
                for(int var7 = 0; var7 < 6; ++var7) {
                    Vector3i var8 = Element.DIRECTIONSi[var7];
                    if ((var18 = var10.getSegment().getSegmentController().getSegmentBuffer().getPointUnsave(new Vector3i(var16.x + var8.x + 16, var16.y + var8.y + 16, var16.z + var8.z + 16))) != null && var18.getType() != 0) {
                        var14.add(var7);
                    }
                }

                SegmentPiece var17 = var10.getSegment().getSegmentController().getSegmentBuffer().getPointUnsave(new Vector3i(var16.x + 16, var16.y + 16, var16.z + 16));
                int var19 = Element.getSide(var2, var17 == null ? null : var17.getAlgorithm(), var16, var17 != null ? var17.getType() : 0, var17 != null ? var17.getOrientation() : 0, var14);
                System.err.println("[GETNEXTTONEAREST] SIDE: " + Element.getSideString(var19) + ": " + var2 + "; " + var16);
                switch(var19) {
                    case 0:
                        var16.z = (int)((float)var16.z + 1.0F);
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 1:
                        var16.z = (int)((float)var16.z - 1.0F);
                        var6.set(var5.x, var5.y, -var5.z);
                        break;
                    case 2:
                        var16.y = (int)((float)var16.y + 1.0F);
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 3:
                        var16.y = (int)((float)var16.y - 1.0F);
                        var6.set(var5.x, -var5.y, var5.z);
                        break;
                    case 4:
                        var16.x = (int)((float)var16.x + 1.0F);
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 5:
                        var16.x = (int)((float)var16.x - 1.0F);
                        var6.set(-var5.x, var5.y, var5.z);
                        break;
                    default:
                        System.err.println("[BUILDMODEDRAWER] WARNING: NO SIDE recognized!!!");
                }

                var16.add(16, 16, 16);
                var18 = new SegmentPiece();
                if ((var18 = this.getSegmentBuffer().getPointUnsave(var16, var18)) == null) {
                    throw new BlockNotBuildTooFast(var16);
                } else {
                    if (var18 != null && var18.getSegment().isEmpty()) {
                        this.getSegmentProvider().getFreeSegmentData().assignData(var18.getSegment());
                    }

                    boolean var11 = false;
                    SegmentCollisionCheckerCallback var12 = new SegmentCollisionCheckerCallback();

                    try {
                        if (var18 != null && this.getCollisionChecker().checkPieceCollision(var18, var12, true)) {
                            var11 = true;
                        }
                    } catch (Exception var9) {
                        var9.printStackTrace();
                    }

                    PlaceElementTestState var15;
                    if (var18 != null && this.getState() instanceof GameClientState && ((GameClientState)this.getState()).getController().getTutorialMode() != null && ((GameClientState)this.getState()).getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof PlaceElementTestState && (var15 = (PlaceElementTestState)((GameClientState)this.getState()).getController().getTutorialMode().getMachine().getFsm().getCurrentState()).getWhere() != null && !var18.equalsPos(var15.getWhere())) {
                        ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_4, 0.0F);
                        return null;
                    } else if (var11) {
                        throw new ElementPositionBlockedException(var12.userData);
                    } else {
                        return var18;
                    }
                }
            }
        } else {
            return null;
        }
    }

    protected abstract String getSegmentControllerTypeString();

    public void handleBeingSalvaged(BeamState var1, BeamHandlerContainer<? extends SimpleTransformableSendableObject> var2, Vector3f var3, SegmentPiece var4, int var5) {
        if (this instanceof TransientSegmentController) {
            ((TransientSegmentController)this).setTouched(true, true);
        }

    }

    public boolean isRepariableFor(RepairBeamHandler var1, String[] var2, Vector3i var3) {
        ManagerContainer var4;
        if (this instanceof ManagedSegmentController && (var4 = ((ManagedSegmentController)this).getManagerContainer()).getRepairDelay() > 0.0F) {
            String var5 = StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_23, new Object[]{(int)Math.ceil((double)var4.getRepairDelay())});
            var2[0] = var5;
            return false;
        } else {
            return true;
        }
    }

    public void powerDamage(float var1, boolean var2) {
        ManagerContainer var3;
        if (var1 > 0.0F && this instanceof ManagedSegmentController && (var3 = ((ManagedSegmentController)this).getManagerContainer()) instanceof PowerManagerInterface) {
            ((PowerManagerInterface)var3).getPowerAddOn().consumePowerInstantly((double)var1, true);
            if (var2) {
                ((PowerManagerInterface)var3).getPowerAddOn().sendPowerUpdate();
            }
        }

    }

    public void cleanUpOnEntityDelete() {
        super.cleanUpOnEntityDelete();
        if (this.isOnServer()) {
            this.acidDamageManagerServer.clear();
        }

    }

    public boolean checkAttack(Damager var1, boolean var2, boolean var3) {
        if (this.isSpectator() || var1 != null && var1 instanceof SimpleTransformableSendableObject && ((SimpleTransformableSendableObject)var1).isSpectator()) {
            if (var1 instanceof SimpleTransformableSendableObject && ((SimpleTransformableSendableObject)var1).isClientOwnObject()) {
                ((GameClientState)this.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_5, 0.0F);
            }

            return false;
        } else {
            if (this.isOnServer()) {
                if (!this.isVulnerable()) {
                    if (var1 != null && var1 instanceof PlayerState) {
                        ((PlayerState)var1).lastSectorProtectedMsgSent = System.currentTimeMillis();
                        ((PlayerState)var1).sendServerMessage(new ServerMessage(new Object[]{26}, 2, ((PlayerState)var1).getId()));
                    }

                    return false;
                }

                PlayerState var5;
                int var6;
                List var7;
                PlayerState var10;
                if (this instanceof PlayerControllable) {
                    Iterator var4 = ((PlayerControllable)this).getAttachedPlayers().iterator();

                    while(var4.hasNext()) {
                        if ((var5 = (PlayerState)var4.next()).isGodMode()) {
                            if (var1 != null && var1 instanceof PlayerControllable) {
                                var7 = ((PlayerControllable)var1).getAttachedPlayers();

                                for(var6 = 0; var6 < var7.size(); ++var6) {
                                    var10 = (PlayerState)var7.get(var6);
                                    if (System.currentTimeMillis() - var10.lastSectorProtectedMsgSent > 5000L) {
                                        var10.lastSectorProtectedMsgSent = System.currentTimeMillis();
                                        var10.sendServerMessage(new ServerMessage(new Object[]{27, var5.getName()}, 2, var10.getId()));
                                    }
                                }
                            }

                            return false;
                        }
                    }
                }

                if (var3 && this.getFactionId() != 0) {
                    Faction var12;
                    if ((var12 = ((FactionState)this.getState()).getFactionManager().getFaction(this.getFactionId())) != null) {
                        var12.onAttackOnServer(var1);
                    } else {
                        System.err.println("[SERVER][EDITABLESEGMENTCONTROLLER] ON HIT: faction not found: " + this.getFactionId());
                    }

                    Fleet var15;
                    if ((var15 = this.getFleet()) != null) {
                        var15.onHitFleetMember(var1, this);
                    }
                }

                Sector var13;
                if ((var13 = ((GameServerState)this.getState()).getUniverse().getSector(this.getSectorId())) != null) {
                    if (var13.isProtected()) {
                        return false;
                    }

                    ((GameServerState)this.getState()).getUniverse().attackInSector(var13.pos);
                }

                if (!this.canAttack(var1)) {
                    return false;
                }

                if (var2 && var1 != null && var1 instanceof SegmentController && ((SegmentController)var1).railController.isInAnyRailRelationWith(this)) {
                    return false;
                }

                if ((var5 = this.isInGodmode()) != null) {
                    if (var1 != null && var1 instanceof PlayerControllable) {
                        var7 = ((PlayerControllable)var1).getAttachedPlayers();

                        for(var6 = 0; var6 < var7.size(); ++var6) {
                            var10 = (PlayerState)var7.get(var6);
                            if (System.currentTimeMillis() - var10.lastSectorProtectedMsgSent > 5000L) {
                                var10.lastSectorProtectedMsgSent = System.currentTimeMillis();
                                var10.sendServerMessage(new ServerMessage(new Object[]{29, var5.getName()}, 2, var10.getId()));
                            }
                        }
                    }

                    return false;
                }
            } else {
                RemoteSector var14;
                if ((var14 = this.getRemoteSector()) != null && var14.isProtectedClient()) {
                    if (var1 != null) {
                        var1.sendClientMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_24, 2);
                    }

                    return false;
                }

                if (this instanceof PlayerControllable) {
                    Iterator var17 = ((PlayerControllable)this).getAttachedPlayers().iterator();

                    while(var17.hasNext()) {
                        PlayerState var9;
                        if ((var9 = (PlayerState)var17.next()).isGodMode()) {
                            if (var1 != null && var1 instanceof PlayerControllable) {
                                List var8 = ((PlayerControllable)var1).getAttachedPlayers();

                                for(int var11 = 0; var11 < var8.size(); ++var11) {
                                    PlayerState var16 = (PlayerState)var8.get(var11);
                                    if (System.currentTimeMillis() - var16.lastSectorProtectedMsgSent > 5000L) {
                                        var16.lastSectorProtectedMsgSent = System.currentTimeMillis();
                                        var16.sendServerMessage(new ServerMessage(new Object[]{30, var9.getName()}, 2, var16.getId()));
                                    }
                                }
                            }

                            return false;
                        }
                    }
                }

                if (!this.canAttack(var1)) {
                    return false;
                }
            }

            return true;
        }
    }

    private void checkBreak(Vector3i var1) {
        for(int var2 = 0; var2 < 6; ++var2) {
            Vector3i var3;
            (var3 = new Vector3i(var1)).add(Element.DIRECTIONSi[var2]);
            SegmentPiece var4 = this.getSegmentBuffer().getPointUnsave(var3);
            System.err.println("CHECKING BREAK OFF PP: " + var3 + ": " + var4.getType());
            if (var4.getType() != 0) {
                System.err.println("CHECKING BREAK OFF: " + var4);
                ((GameServerState)this.getState()).getController().queueSegmentControllerBreak(var4);
            }
        }

    }

    private boolean isEnterable(short var1) {
        return var1 != 0 && ElementKeyMap.getInfo(var1).isEnterable();
    }

    public boolean isFlagCharacterExitCheckByExplosion() {
        return this.flagCharacterExitCheckByExplosion;
    }

    public void setFlagCharacterExitCheckByExplosion(boolean var1) {
        this.flagCharacterExitCheckByExplosion = var1;
    }

    public boolean needsManifoldCollision() {
        return this.getElementClassCountMap().get((short)14) > 0;
    }

    protected abstract void onCoreDestroyed(Damager var1);

    protected void onCoreHitAlreadyDestroyed(float var1) {
    }

    public void onDamageServerRootObject(float var1, Damager var2) {
        this.lastDamageTaken = this.getState().getUpdateTime();
    }

    private void removeConfirmDialog(final SegmentPiece var1, final short var2, final BuildRemoveCallback var3, final boolean var4, final BuildInstruction var5, String var6) {
        (new PlayerGameOkCancelInput("CONFIRM", (GameClientState)this.getState(), Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_20, var6) {
            public boolean isOccluded() {
                return false;
            }

            public void onDeactivate() {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().hinderInteraction(400);
            }

            public void pressedOK() {
                if (EditableSendableSegmentController.this.removeBlock(var1, var2, var3, var4, var5) != 0) {
                    this.getState().getController().queueUIAudio("0022_action - buttons push medium");
                    this.deactivate();
                }

            }
        }).activate();
    }

    public void remove(int var1, int var2, int var3, BuildRemoveCallback var4, boolean var5, Set<Segment> var6, short var7, short var8, int var9, BuildHelper var10, BuildInstruction var11) {
        if (var10 == null || var10.contains(var1, var2, var3)) {
            SegmentPiece var12;
            if ((var12 = this.getSegmentBuffer().getPointUnsave(var1, var2, var3)) != null && var12.getType() != 0) {
                short var13 = var12.getType();
                if (var7 != 32767 && var13 != var7) {
                    return;
                }

                if (!var4.canRemove(var13)) {
                    return;
                }

                boolean var14 = false;
                String var23;
                if (1 == var13 || this instanceof SpaceStation && this.getTotalElements() == 1) {
                    if (var12.equalsPos(Ship.core) || this instanceof SpaceStation) {
                        var14 = true;
                        if (!this.isOnServer()) {
                            if (this.getTotalElements() == 1) {
                                var23 = this instanceof Ship ? Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_13 : Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_14;
                                this.removeConfirmDialog(var12, var13, var4, true, var11, var23);
                            } else {
                                ((GameClientState)this.getState()).getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_11, 0.0F);
                            }
                        }
                    }
                } else {
                    SegmentController var15;
                    if (120 != var13 && 677 != var13 && 347 != var13 && !ElementKeyMap.isMacroFactory(var13)) {
                        if (291 == var13) {
                            SegmentController var10000 = var12.getSegment().getSegmentController();
                            var15 = null;
                            if (var10000.getFactionId() != 0) {
                                var14 = true;
                                var23 = StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_21, new Object[]{ElementKeyMap.getInfoFast(var13).getName(), var12.getPos(new Vector3b()).toString()});
                                this.removeConfirmDialog(var12, var13, var4, true, var11, var23);
                            }
                        } else if (542 == var13 && (var15 = var12.getSegment().getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var15).getManagerContainer() instanceof StationaryManagerContainer) {
                            StationaryManagerContainer var16 = (StationaryManagerContainer)((ManagedSegmentController)var15).getManagerContainer();

                            for(int var27 = 0; var27 < var16.getWarpgate().getCollectionManagers().size(); ++var27) {
                                WarpgateCollectionManager var18;
                                if ((var18 = (WarpgateCollectionManager)var16.getWarpgate().getCollectionManagers().get(var27)).getControllerIndex() == var12.getAbsoluteIndex() && !var18.getWarpDestinationUID().equals("none")) {
                                    var14 = true;
                                    var23 = StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_22, new Object[]{ElementKeyMap.getInfoFast(var13).getName(), var12.getPos(new Vector3b()).toString()});
                                    this.removeConfirmDialog(var12, var13, var4, true, var11, var23);
                                }
                            }
                        }
                    } else {
                        Inventory var17;
                        if ((var15 = var12.getSegment().getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var15).getManagerContainer() instanceof InventoryHolder && (var17 = ((ManagedSegmentController)var15).getManagerContainer().getInventory(var12.getAbsoluteIndex())) != null && !var17.isEmpty()) {
                            var14 = true;
                            var23 = StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_19, new Object[]{ElementKeyMap.getInfoFast(var13).getName(), var12.getPos(new Vector3b()).toString()});
                            this.removeConfirmDialog(var12, var13, var4, true, var11, var23);
                        }
                    }
                }

                if (!var14) {
                    byte var26 = var12.getOrientation();
                    boolean var25 = var12.isActive();
                    byte var28 = var26;
                    boolean var29 = var25;
                    if (ElementKeyMap.isValidType(var8) && this.allowedType(var8) && this.checkAllPlace(var8, 1, (SymmetryPlane)null) > 0) {
                        int var21 = var11.getAdds().size();
                        int var24 = var11.getRemoves().size();
                        this.removeBlock(var12, var13, var4, var5, var11);
                        var6.add(var12.getSegment());
                        ElementInformation var19 = ElementKeyMap.getInfo(var8);
                        ElementInformation var20 = ElementKeyMap.getInfo(var13);
                        BlockOrientation var22 = ElementInformation.convertOrientation(ElementKeyMap.getInfo(var8), (byte)var9);
                        if (var19.orientatable != var20.orientatable || var19.getIndividualSides() != var20.getIndividualSides() || var19.getBlockStyle() != var20.getBlockStyle() || var19.resourceInjection != ResourceInjectionType.OFF) {
                            var28 = var22.blockOrientation;
                            var29 = var22.activateBlock;
                        }

                        if (var8 == 689) {
                            var28 = 0;
                            var29 = false;
                        }

                        if (!var19.canActivate() && var19.getBlockStyle() == BlockStyle.NORMAL) {
                            var29 = var22.activateBlock;
                        }

                        this.build(var1, var2, var3, var8, var28, var29, var4, new Vector3i(), new int[]{0, 1}, var10, var11);

                        while(var11.getAdds().size() > var21) {
                            var11.getAdds().removeQuick(var11.getAdds().size() - 1);
                        }

                        while(var11.getRemoves().size() > var24) {
                            var11.getRemoves().removeQuick(var11.getRemoves().size() - 1);
                        }

                        var11.recordReplace(ElementCollection.getIndex(var1, var2, var3), var13, var8, var26, var25);
                        return;
                    }

                    if ((var7 == 32767 || var7 == var12.getType()) && (!ElementKeyMap.isValidType(var8) || this.allowedType(var8))) {
                        this.removeBlock(var12, var13, var4, var5, var11);
                        var6.add(var12.getSegment());
                    }
                }
            }

        }
    }

    public void removeInSymmetry(int var1, int var2, int var3, BuildRemoveCallback var4, boolean var5, Set<Segment> var6, short var7, short var8, int var9, BuildHelper var10, BuildInstruction var11, SymmetryPlane var12) {
        int var14;

        switch(var12.getMode()) {
            case XY:
                var14 = (var12.getPlane().z - var3 << 1) + var12.getExtraDist();
                this.remove(var1, var2, var3 + var14, var4, var5, var6, var7, var8, var12.getMirrorOrientation(var8, var9), var10, var11);
                break;
            case XZ:
                var14 = (var12.getPlane().y - var2 << 1) + var12.getExtraDist();
                this.remove(var1, var2 + var14, var3, var4, var5, var6, var7, var8, var12.getMirrorOrientation(var8, var9), var10, var11);
                break;
            case YZ:
                var14 = (var12.getPlane().x - var1 << 1) + var12.getExtraDist();
                this.remove(var1 + var14, var2, var3, var4, var5, var6, var7, var8, var12.getMirrorOrientation(var8, var9), var10, var11);
                break;
        }
    }

    public void redo(BuildInstruction var1, BuildInstruction var2) {
        ObjectOpenHashSet var3 = new ObjectOpenHashSet();
        boolean var4 = var1.getAdds().size() == 1 && var1.getRemoves().isEmpty() || var1.getRemoves().size() == 1 && var1.getAdds().isEmpty();
        SegmentPiece var5 = new SegmentPiece();
        Iterator var6 = var1.getReplaces().iterator();

        while(true) {
            Replace var7;
            SegmentPiece var8;
            int var13;
            do {
                if (!var6.hasNext()) {
                    for(var6 = var1.getRemoves().iterator(); var6.hasNext(); this.sendBlockMod(new RemoteSegmentPiece(var8, this.getNetworkObject()))) {
                        var8 = ((Remove)var6.next()).where;
                        var2.recordRemove(var8);
                        boolean var18 = var8.getSegment().removeElement(var8.x, var8.y, var8.z, var4);
                        var3.add(var8.getSegment());
                        if (var18) {
                            this.lastEditBlocks = this.getState().getUpdateTime();
                            ((RemoteSegment)var8.getSegment()).setLastChanged(this.getState().getUpdateTime());
                        }
                    }

                    int var14 = 0;
                    Iterator var15 = var1.getAdds().iterator();

                    while(true) {
                        Add var17;
                        long var19;
                        SegmentPiece var24;
                        do {
                            if (!var15.hasNext()) {
                                if (var14 == 0) {
                                    var15 = var1.getAdds().iterator();

                                    while(var15.hasNext()) {
                                        var17 = (Add)var15.next();
                                        Vector3i var21 = new Vector3i();
                                        final Add finalVar1 = var17;
                                        BuildCallback var23 = new BuildCallback() {
                                            public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                                            }

                                            public long getSelectedControllerPos() {
                                                return finalVar1.selectedController;
                                            }
                                        };
                                        int var25 = ElementCollection.getPosX(var17.where);
                                        int var27 = ElementCollection.getPosY(var17.where);
                                        var13 = ElementCollection.getPosZ(var17.where);
                                        this.build(var25, var27, var13, var17.type, var17.elementOrientation, var17.activateBlock, var23, var21, new int[]{0, 1}, (BuildHelper)null, var2);
                                    }
                                } else {
                                    ((GameClientController)this.getState().getController()).popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_16, new Object[]{var14}), "BLOCKED_POS", 0.0F);
                                }

                                var15 = var3.iterator();

                                while(var15.hasNext()) {
                                    Segment var20;
                                    if (!(var20 = (Segment)var15.next()).isEmpty()) {
                                        var20.getSegmentData().restructBB(true);
                                    } else {
                                        this.getSegmentBuffer().restructBB();
                                    }
                                }

                                return;
                            }

                            var19 = (var17 = (Add)var15.next()).where;
                        } while((var24 = this.getSegmentBuffer().getPointUnsave(var17.where, var5)) != null && var24.getType() != 0);

                        VoidSegmentPiece var26;
                        (var26 = new VoidSegmentPiece()).voidPos = ElementCollection.getPosFromIndex(var19, new Vector3i());
                        var26.setType(var17.type);
                        var26.setOrientation((byte)var17.elementOrientation);
                        var26.setActive(var17.activateBlock);
                        SegmentCollisionCheckerCallback var28 = new SegmentCollisionCheckerCallback();
                        if (this.getCollisionChecker().checkPieceCollision(var26, var28, false)) {
                            ++var14;
                        }
                    }
                }

                var7 = (Replace)var6.next();
            } while((var8 = this.getSegmentBuffer().getPointUnsave(var7.where, var5)) == null);

            int var9 = var2.getAdds().size();
            if (var1.fillTool != null) {
                var1.fillTool.redo(var8.getAbsoluteIndex());
            }

            boolean var10 = var8.getSegment().removeElement(var8.x, var8.y, var8.z, var4);
            var3.add(var8.getSegment());
            if (var10) {
                this.lastEditBlocks = System.currentTimeMillis();
                ((RemoteSegment)var8.getSegment()).setLastChanged(System.currentTimeMillis());
            }

            var8.refresh();
            this.sendBlockMod(new RemoteSegmentPiece(new SegmentPiece(var8), this.getNetworkObject()));
            Vector3i var11 = new Vector3i();
            BuildCallback var12 = new BuildCallback() {
                public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                }

                public long getSelectedControllerPos() {
                    return -9223372036854775808L;
                }
            };
            var13 = ElementCollection.getPosX(var7.where);
            int var16 = ElementCollection.getPosY(var7.where);
            int var22 = ElementCollection.getPosZ(var7.where);
            this.build(var13, var16, var22, var7.to, var7.prevOrientation, var7.prevIsActive, var12, var11, new int[]{0, 1}, (BuildHelper)null, var2);

            while(var2.getAdds().size() > var9) {
                var2.getAdds().removeQuick(var2.getAdds().size() - 1);
            }

            var2.recordReplace(var7.where, var7.from, var7.to, var7.prevOrientation, var7.prevIsActive);
        }
    }

    public void undo(BuildInstruction var1, BuildInstruction var2) {
        ObjectOpenHashSet var3 = new ObjectOpenHashSet();
        boolean var4 = var1.getAdds().size() == 1 && var1.getRemoves().isEmpty() || var1.getRemoves().size() == 1 && var1.getAdds().isEmpty();
        SegmentPiece var5 = new SegmentPiece();
        int var6 = var1.getAdds().size();
        boolean var7 = false;
        Iterator var8 = var1.getReplaces().iterator();

        while(true) {
            SegmentPiece var10;
            while(var8.hasNext()) {
                Replace var9 = (Replace)var8.next();
                if (!((GameClientState)this.getState()).getPlayer().getInventory().canPutIn(var9.to, 1)) {
                    var7 = true;
                } else if ((var10 = this.getSegmentBuffer().getPointUnsave(var9.where, var5)) != null) {
                    if (var1.fillTool != null) {
                        var1.fillTool.undo(var10.getAbsoluteIndex());
                    }

                    int var11 = var2.getAdds().size();
                    boolean var12 = var10.getSegment().removeElement(var10.x, var10.y, var10.z, var4);
                    var3.add(var10.getSegment());
                    if (var12) {
                        this.lastEditBlocks = System.currentTimeMillis();
                        ((RemoteSegment)var10.getSegment()).setLastChanged(System.currentTimeMillis());
                    }

                    var10.refresh();
                    this.sendBlockMod(new RemoteSegmentPiece(new SegmentPiece(var10), this.getNetworkObject()));
                    Vector3i var13 = new Vector3i();
                    BuildCallback var14 = new BuildCallback() {
                        public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                        }

                        public long getSelectedControllerPos() {
                            return -9223372036854775808L;
                        }
                    };
                    int var25 = ElementCollection.getPosX(var9.where);
                    int var15 = ElementCollection.getPosY(var9.where);
                    int var16 = ElementCollection.getPosZ(var9.where);
                    this.build(var25, var15, var16, var9.from, var9.prevOrientation, var9.prevIsActive, var14, var13, new int[]{0, 1}, (BuildHelper)null, var2);
                    var10.refresh();

                    while(var2.getAdds().size() > var11) {
                        var2.getAdds().removeQuick(var2.getAdds().size() - 1);
                    }

                    var2.recordReplace(var9.where, var9.to, var9.from, var10.getOrientation(), var10.isActive());
                }
            }

            int var17;
            for(var17 = 0; var17 < var6; ++var17) {
                Add var18 = (Add)var1.getAdds().get(var17);
                if ((var10 = this.getSegmentBuffer().getPointUnsave(var18.where, var5)) != null) {
                    if (!((GameClientState)this.getState()).getPlayer().getInventory().canPutIn(var18.type, 1)) {
                        var7 = true;
                    } else {
                        if (var1.fillTool != null) {
                            var1.fillTool.undo(var10.getAbsoluteIndex());
                        }

                        var2.recordRemove(new SegmentPiece(var10));
                        boolean var22 = var10.getSegment().removeElement(var10.x, var10.y, var10.z, var4);
                        var3.add(var10.getSegment());
                        if (var22) {
                            this.lastEditBlocks = System.currentTimeMillis();
                            ((RemoteSegment)var10.getSegment()).setLastChanged(System.currentTimeMillis());
                        }

                        var10.refresh();
                        this.sendBlockMod(new RemoteSegmentPiece(new SegmentPiece(var10), this.getNetworkObject()));
                    }
                }
            }

            if (var7) {
                ((GameClientController)this.getState().getController()).popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_12, 0.0F);
            }

            var17 = 0;
            Iterator var19 = var1.getRemoves().iterator();

            while(var19.hasNext()) {
                SegmentPiece var23 = ((Remove)var19.next()).where;
                SegmentCollisionCheckerCallback var26 = new SegmentCollisionCheckerCallback();
                if (this.getCollisionChecker().checkPieceCollision(var23, var26, false)) {
                    ++var17;
                }
            }

            if (var17 == 0) {
                var19 = var1.getRemoves().iterator();

                label94:
                while(true) {
                    Remove var20;
                    SegmentPiece var28;
                    do {
                        Vector3i var24;
                        BuildCallback var27;
                        do {
                            if (!var19.hasNext()) {
                                break label94;
                            }

                            var20 = (Remove)var19.next();
                            var24 = new Vector3i();
                            final Remove finalVar2 = var20;
                            var27 = new BuildCallback() {
                                public void onBuild(Vector3i var1, Vector3i var2, short var3) {
                                }

                                public long getSelectedControllerPos() {
                                    return finalVar2.controller;
                                }
                            };
                        } while((var28 = var20.where).getType() == 0);

                        Vector3i var30 = var28.getAbsolutePos(new Vector3i());
                        this.build(var30.x, var30.y, var30.z, var28.getType(), var28.getOrientation(), var28.isActive(), var27, var24, new int[]{0, 1}, (BuildHelper)null, var2);
                    } while(var20.connectedFromThis == null);

                    Iterator var29 = var20.connectedFromThis.iterator();

                    while(var29.hasNext()) {
                        long var31 = (Long)var29.next();
                        this.getControlElementMap().removeControlledFromAll(ElementCollection.getPosIndexFrom4(var31), (short)ElementCollection.getType(var31), true);
                        this.getControlElementMap().addControllerForElement(var28.getAbsoluteIndex(), ElementCollection.getPosIndexFrom4(var31), (short)ElementCollection.getType(var31));
                    }
                }
            } else {
                ((GameClientController)this.getState().getController()).popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_COMMON_CONTROLLER_EDITABLESENDABLESEGMENTCONTROLLER_17, new Object[]{var17}), "BLOCKED_POS", 0.0F);
            }

            var19 = var3.iterator();

            while(var19.hasNext()) {
                Segment var21;
                if (!(var21 = (Segment)var19.next()).isEmpty()) {
                    var21.getSegmentData().restructBB(true);
                } else {
                    this.getSegmentBuffer().restructBB();
                }
            }

            return;
        }
    }

    private short removeBlock(SegmentPiece var1, short var2, BuildRemoveCallback var3, boolean var4, BuildInstruction var5) {
        var5.recordRemove(var1);
        var1.getSegment().removeElement(var1.x, var1.y, var1.z, var4);
        this.lastEditBlocks = System.currentTimeMillis();
        ((RemoteSegment)var1.getSegment()).setLastChanged(System.currentTimeMillis());
        var1.refresh();
        this.sendBlockMod(new RemoteSegmentPiece(var1, this.getNetworkObject()));
        var3.onRemove(var1.getAbsoluteIndex(), var2);
        return var2;
    }

    public String toString() {
        return this.getSegmentControllerTypeString() + "(" + this.getId() + ")";
    }

    public boolean handleCollision(int var1, RigidBody var2, RigidBody var3, SolverConstraint var4) {
        Sendable var5;
        if ((var5 = (Sendable)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(this.getSectorId())) != null && var5 instanceof RemoteSector) {
            if (((RemoteSector)var5).isProtectedClient()) {
                return false;
            } else {
                ManifoldPoint var19 = (ManifoldPoint)var4.originalContactPoint;
                Sendable var6;
                if ((var6 = (Sendable)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var19.starMadeIdA)) != null && var6.getId() != this.getId() && var6 instanceof EditableSendableSegmentController && ((EditableSendableSegmentController)var6).railController.isInAnyRailRelationWith(this)) {
                    return ((EditableSendableSegmentController)var6).handleCollision(var1, var2, var3, var4);
                } else {
                    if (!this.isOnServer() && this.uNumMagnetDock != this.getState().getNumberOfUpdate() && ElementKeyMap.isValidType(var19.starMadeTypeA) && ElementKeyMap.isValidType(var19.starMadeTypeB) && (ElementKeyMap.getInfoFast(var19.starMadeTypeA).isRailDockable() && ElementKeyMap.getInfoFast(var19.starMadeTypeB).isRailDocker() || ElementKeyMap.getInfoFast(var19.starMadeTypeB).isRailDockable() && ElementKeyMap.getInfoFast(var19.starMadeTypeA).isRailDocker())) {
                        SegmentController var12 = (SegmentController)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var19.starMadeIdA);
                        SegmentController var14 = (SegmentController)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var19.starMadeIdB);
                        if (var12 == null || var14 == null) {
                            return false;
                        }

                        SegmentPiece var20 = var12.getSegmentBuffer().getPointUnsave(var19.starMadeXA + 16, var19.starMadeYA + 16, var19.starMadeZA + 16);
                        SegmentPiece var7 = var14.getSegmentBuffer().getPointUnsave(var19.starMadeXB + 16, var19.starMadeYB + 16, var19.starMadeZB + 16);
                        if (var20 != null && var7 != null) {
                            assert var20.getType() == var19.starMadeTypeA && var7.getType() == var19.starMadeTypeB : var20 + "; " + var19.starMadeTypeA + " ::: " + var7 + "; " + var19.starMadeTypeB;

                            DockingFailReason var8;
                            if (!var12.railController.isDockedOrDirty() && ElementKeyMap.getInfoFast(var19.starMadeTypeA).isRailDocker()) {
                                var8 = new DockingFailReason();
                                if (var12.railController.isOkToDockClientCheck(var20, var7, var8)) {
                                    var12.railController.connectClient(var20, var7);
                                } else {
                                    var8.popupClient(var12);
                                }
                            } else if (!var14.railController.isDockedOrDirty()) {
                                var8 = new DockingFailReason();
                                if (var14.railController.isOkToDockClientCheck(var7, var20, var8)) {
                                    var14.railController.connectClient(var7, var20);
                                } else {
                                    var8.popupClient(var14);
                                }
                            }

                            this.uNumMagnetDock = this.getState().getNumberOfUpdate();
                        }
                    }

                    if (this.isOnServer() && (var19.starMadeTypeA == 14 || var19.starMadeTypeB == 14)) {
                        ExplosiveManagerContainerInterface var13 = null;
                        Sendable var15;
                        short var21;
                        if (var1 == 0) {
                            this.local.set(var19.starMadeXA, var19.starMadeYA, var19.starMadeZA);
                            var21 = var19.starMadeTypeA;
                            var15 = (Sendable)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var19.starMadeIdB);
                            var13 = (ExplosiveManagerContainerInterface)((ManagedSegmentController)this).getManagerContainer();
                        } else {
                            this.local.set(var19.starMadeXB, var19.starMadeYB, var19.starMadeZB);
                            var21 = var19.starMadeTypeB;
                            if ((var15 = (Sendable)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var19.starMadeIdB)) instanceof ManagedSegmentController && ((ManagedSegmentController)var15).getManagerContainer() instanceof ExplosiveManagerContainerInterface) {
                                var13 = (ExplosiveManagerContainerInterface)((ManagedSegmentController)var15).getManagerContainer();
                            }
                        }

                        if (var21 == 14 && var15 != null && var15 instanceof EditableSendableSegmentController && !var13.getExplosiveCollectionManager().getElementCollections().isEmpty()) {
                            Vector3i var10000 = this.local;
                            var10000.x += 16;
                            var10000 = this.local;
                            var10000.y += 16;
                            var10000 = this.local;
                            var10000.z += 16;
                            var19.getPositionWorldOnB(this.tmpPosA);
                            this.getWorldTransformInverse().transform(this.tmpPosA);
                            var13.getExplosiveElementManager().addExplosion(this.local, this.tmpPosA);
                        }
                    }

                    if (this.isOnServer() && this.getMass() > 0.0F && ServerConfig.COLLISION_DAMAGE.isOn()) {
                        if (!(var4.appliedImpulse > (Float)ServerConfig.COLLISION_DAMAGE_THRESHOLD.getCurrentState())) {
                            return false;
                        }

                        if (this.isInGodmode() == null) {
                            Vector3i var16 = this.v.local;
                            if (var1 == 0) {
                                var16.set(var19.starMadeXA, var19.starMadeYA, var19.starMadeZA);
                            } else {
                                var16.set(var19.starMadeXB, var19.starMadeYB, var19.starMadeZB);
                            }

                            var16.x += 16;
                            var16.y += 16;
                            var16.z += 16;
                            SegmentPiece var18;
                            if ((var18 = this.getSegmentBuffer().getPointUnsave(var16, this.v.tmpPiece)) != null && var18.isValid() && var18.isAlive()) {
                                int var22 = SegmentData.getInfoIndex(var18.x, var18.y, var18.z);
                                SegmentData var23 = var18.getSegment().getSegmentData();
                                short var11 = var18.getType();
                                float var17 = this.damageElement(var11, var22, var23, 600, (Damager)null, DamageDealerType.GENERAL, -9223372036854775808L);
                                System.err.println("[COLLISION DAMAGE] " + this.getState() + "; " + this + "; " + var18 + " damaged: " + var17);
                                if (var23.getHitpointsByte(var22) > 0) {
                                    var18.refresh();
                                    this.sendBlockMod(new RemoteSegmentPiece(new SegmentPiece(var18), this.getNetworkObject()));
                                    this.onBlockDamage(var18.getAbsoluteIndex(), var11, 600, DamageDealerType.GENERAL, (Damager)null);
                                } else {
                                    if (var11 == this.getCoreType() && var18.getAbsolutePos(this.absPosCache).equals(Ship.core)) {
                                        try {
                                            var23.setHitpointsByte(var22, 0);
                                        } catch (SegmentDataWriteException var10) {
                                            var23 = SegmentDataWriteException.replaceData(var23.getSegment());

                                            try {
                                                var23.setHitpointsByte(var22, 0);
                                            } catch (SegmentDataWriteException var9) {
                                                var9.printStackTrace();
                                                throw new RuntimeException(var9);
                                            }
                                        }

                                        this.onCoreDestroyed((Damager)null);
                                        this.onCoreHitAlreadyDestroyed(600.0F);
                                        var18.refresh();
                                        this.sendBlockMod(new RemoteSegmentPiece(new SegmentPiece(var18), this.getNetworkObject()));
                                        this.onBlockDamage(var18.getAbsoluteIndex(), var11, 600, DamageDealerType.GENERAL, (Damager)null);
                                    } else {
                                        this.killBlock(var18);
                                    }

                                    if (ServerConfig.ENABLE_BREAK_OFF.isOn()) {
                                        var18.getAbsolutePos(this.absPosCache);
                                        this.checkBreak(this.absPosCache);
                                    }

                                    if (this.isEnterable(var11)) {
                                        this.forceCharacterExit(var18);
                                    }
                                }

                                ((RemoteSegment)var18.getSegment()).setLastChanged(System.currentTimeMillis());
                            }

                            return true;
                        }
                    }

                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public boolean killBlock(SegmentPiece var1) {
        boolean var2 = var1.getSegment().removeElement(var1.x, var1.y, var1.z, true);
        this.onBlockKill(var1, (Damager)null);
        var1.refresh();
        this.sendBlockKill(var1);
        return var2;
    }

    public AcidDamageManager getAcidDamageManagerServer() {
        return this.acidDamageManagerServer;
    }

    public boolean isExtraAcidDamageOnDecoBlocks() {
        return false;
    }

    public class DryTestBuild implements BuilderInterface {
        public BoundingBox boundingBox = new BoundingBox();

        public DryTestBuild() {
        }

        public void build(int var1, int var2, int var3, short var4, int var5, boolean var6, BuildSelectionCallback var7, Vector3i var8, int[] var9, BuildHelper var10, BuildInstruction var11) {
            this.boundingBox.min.x = Math.min(this.boundingBox.min.x, (float)var1);
            this.boundingBox.min.y = Math.min(this.boundingBox.min.y, (float)var2);
            this.boundingBox.min.z = Math.min(this.boundingBox.min.z, (float)var3);
            this.boundingBox.max.x = Math.max(this.boundingBox.max.x, (float)(var1 + 1));
            this.boundingBox.max.y = Math.max(this.boundingBox.max.y, (float)(var2 + 1));
            this.boundingBox.max.z = Math.max(this.boundingBox.max.z, (float)(var3 + 1));
            SegmentPiece var12;
            if ((var12 = EditableSendableSegmentController.this.getSegmentBuffer().getPointUnsave(new Vector3i(var1, var2, var3))) != null) {
                SegmentCollisionCheckerCallback var13 = new SegmentCollisionCheckerCallback();
                if (EditableSendableSegmentController.this.getCollisionChecker().checkPieceCollision(var12, var13, false)) {
                    System.err.println(EditableSendableSegmentController.this.getState() + "; " + this + " Block at " + var12 + " blocked");
                    throw new PositionBlockedException();
                }
            }

        }
    }
}