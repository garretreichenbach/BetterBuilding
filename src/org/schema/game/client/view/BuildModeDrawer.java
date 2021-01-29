package org.schema.game.client.view;

import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryMode;
import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.lwjgl.opengl.GL11;
import org.schema.common.FastMath;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.PolygonToolsVars;
import org.schema.common.util.linAlg.TransformTools;
import org.schema.common.util.linAlg.Triangle;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.SegmentBuildController;
import org.schema.game.client.controller.manager.ingame.SegmentControlManager;
import org.schema.game.client.controller.manager.ingame.SymmetryPlanes;
import org.schema.game.client.controller.manager.ingame.character.PlayerExternalController;
import org.schema.game.client.controller.manager.ingame.ship.ShipControllerManager;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.camera.BuildShipCamera;
import org.schema.game.client.view.cubes.shapes.BlockShapeAlgorithm;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.client.view.cubes.shapes.orientcube.Oriencube;
import org.schema.game.client.view.effects.ConstantIndication;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.game.client.view.tools.SingleBlockDrawer;
import org.schema.game.common.controller.ArmorCheckTraverseHandler;
import org.schema.game.common.controller.ArmorValue;
import org.schema.game.common.controller.PositionControl;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ControlBlockElementCollectionManager;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.ManagerModuleCollection;
import org.schema.game.common.controller.elements.ShieldContainerInterface;
import org.schema.game.common.controller.elements.ShieldLocal;
import org.schema.game.common.controller.elements.StabBonusCalcStyle;
import org.schema.game.common.controller.elements.UsableControllableElementManager;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.controller.elements.dockingBlock.DockingBlockCollectionManager;
import org.schema.game.common.controller.elements.power.reactor.MainReactorUnit;
import org.schema.game.common.controller.elements.power.reactor.PowerInterface;
import org.schema.game.common.controller.elements.power.reactor.StabilizerUnit;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorTree;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.VoidUniqueSegmentPiece;
import org.schema.game.common.data.creature.AICreature;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementClassNotFoundException;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.physics.InnerSegmentIterator;
import org.schema.game.common.data.physics.ModifiedDynamicsWorld;
import org.schema.game.common.data.physics.PairCachingGhostObjectAlignable;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.physics.RigidBodySegmentController;
import org.schema.game.common.data.player.AbstractCharacter;
import org.schema.game.common.data.player.PlayerCharacter;
import org.schema.game.common.data.world.GameTransformable;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.Drawable;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.Mesh;
import org.schema.schine.graphicsengine.forms.debug.DebugLine;
import org.schema.schine.graphicsengine.shader.Shader;
import org.schema.schine.graphicsengine.shader.ShaderLibrary;
import org.schema.schine.graphicsengine.util.timer.LinearTimerUtil;
import org.schema.schine.graphicsengine.util.timer.SinusTimerUtil;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;

/**
 * BuildModeDrawer.java
 * ==================================================
 * Modified 01/29/2021 by TheDerpGamer
 * @author Schema
 */
public class BuildModeDrawer implements Drawable {
    public static SegmentPiece currentPiece;
    public static ElementInformation currentInfo;
    public static int currentSide;
    public static SegmentPiece selectedBlock;
    public static ElementInformation selectedInfo;
    public static float currentOptStabDist;
    public static float currentStabDist;
    private final Vector3f dist = new Vector3f();
    private final SingleBlockDrawer drawer = new SingleBlockDrawer();
    Transform t = new Transform();
    Transform tinv = new Transform();
    Vector3i pp = new Vector3i();
    Vector3i posTmp = new Vector3i();
    int i = 0;
    private boolean firstDraw = true;
    private GameClientState state;
    public CubeRayCastResult testRayCollisionPoint;
    private LinearTimerUtil linearTimer;
    private LinearTimerUtil linearTimerC;
    private ConstantIndication indication;
    private Vector3b lastCubePos = new Vector3b();
    private Segment lastSegment = null;
    private Mesh mesh;
    private int blockOrientation = -1;
    private long blockChangedTime;
    private SelectionShader selectionShader;
    private SelectionShader selectionShaderSolid;
    private Vector3i toBuildPos = new Vector3i();
    private Vector3i loockingAtPos = new Vector3i();
    private Vector3f pTmp = new Vector3f();
    private boolean flagUpdate;
    private GameTransformable currentObject;
    private boolean drawDebug;
    StringBuffer touching = new StringBuffer();
    private final ShortOpenHashSet conDrw = new ShortOpenHashSet();
    private LinearTimerUtil linearTimerSl = new LinearTimerUtil(1.0F);
    private final PolygonToolsVars v = new PolygonToolsVars();
    public static long currentPieceIndexIntegrity;
    public static double currentPieceIntegrity;
    public static double currentStabEfficiency;
    public static boolean inReactorAlignSlider;
    public static boolean inReactorAlignAlwaysVisible;
    public static int inReactorAlignSliderSelectedAxis = -1;
    private final Vector3f tmp = new Vector3f();
    private final Vector4f stabColor = new Vector4f();
    private final SinusTimerUtil colorMod = new SinusTimerUtil(7.0F);
    private int currentSelectedStabSide = -1;
    private final ArmorCheckTraverseHandler pt = new ArmorCheckTraverseHandler();
    private CubeRayCastResult rayCallbackTraverse = new CubeRayCastResult(new Vector3f(), new Vector3f(), (Object)null, new SegmentController[0]) {
        public InnerSegmentIterator newInnerSegmentIterator() {
            return BuildModeDrawer.this.pt;
        }
    };
    public static final ArmorValue armorValue = new ArmorValue();
    private final Vector3f cPosA = new Vector3f();
    private final Vector3f cPosB = new Vector3f();
    private long lastArmorCheck;

    public BuildModeDrawer(GameClientState var1) {
        this.state = var1;
        this.linearTimer = new LinearTimerUtil();
        this.linearTimerC = new LinearTimerUtil(6.1F);
        this.indication = new ConstantIndication(new Transform(), "");
    }

    public void addBlockedDockIndicator(SegmentPiece var1, SegmentController var2, DockingBlockCollectionManager var3) {
    }

    public void cleanUp() {
        this.drawer.cleanUp();
    }

    public void draw() {
        if (this.firstDraw) {
            this.onInit();
            GlUtil.printGlErrorCritical();
        }

        if (!EngineSettings.G_DRAW_NO_OVERLAYS.isOn()) {
            if (this.touching.length() > 0) {
                this.touching.delete(0, this.touching.length());
            }

            this.drawDebug = WorldDrawer.drawError;
            if (this.drawDebug) {
                GlUtil.printGlErrorCritical();
            }

            SimpleTransformableSendableObject var1 = this.state.getCurrentPlayerObject();
            if (!this.getShipControllerManager().getSegmentBuildController().isTreeActive() && this.getPlayerManager().isActive()) {
                WorldDrawer.insideBuildMode = false;
                this.drawCharacterExternalMode();
            } else {
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                if (var1 instanceof SegmentController && this.getPlayerIntercationManager().isInAnyStructureBuildMode()) {
                    SegmentController var2 = (SegmentController)var1;
                    this.drawFor(var2);
                }

            }
        }
    }

    public void drawForAll(SegmentController var1) {
        var1 = var1.railController.getRoot();
        this.drawForChilds(var1);
    }

    public void drawForChilds(SegmentController var1) {
        this.drawFor(var1);
        Iterator var3 = var1.railController.next.iterator();

        while(var3.hasNext()) {
            RailRelation var2 = (RailRelation)var3.next();
            this.drawForChilds(var2.docked.getSegmentController());
        }

    }

    private void drawFor(SegmentController var1) {
        this.currentSelectedStabSide = -1;
        if (var1 != null) {
            if (this.drawDebug) {
                GlUtil.printGlErrorCritical();
            }

            ManagerContainer var2;
            if (var1 instanceof ManagedSegmentController && (var2 = ((ManagedSegmentController)var1).getManagerContainer()).isUsingPowerReactors() && this.getPlayerIntercationManager().getSelectedTypeWithSub() == 1009) {
                var2.getStabilizer().drawMesh();
            }

            this.drawPowerHull(var1, true);
            if (this.drawDebug) {
                GlUtil.printGlErrorCritical();
            }

            WorldDrawer.insideBuildMode = true;
            boolean var4;
            Transform var5;
            ArrayList<SymmetryPlane> var14 = this.getActiveBuildController().getAllSymmetryPlanes();
            Vector3f var10000;
            if (var1 instanceof Ship) {
                BuildToolsManager var8;
                boolean var3 = (var8 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).isAddMode();
                if (var8.getBuildHelper() != null) {
                    var8.getBuildHelper().draw();
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                GlUtil.glPushMatrix();
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                var4 = true;
                if (!ElementKeyMap.isValidType(this.getPlayerIntercationManager().getSelectedTypeWithSub())) {
                    var4 = false;
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                var5 = this.getToBuildTransform(var1);
                if (var8.isInCreateDockingMode() && var8.getBuildToolCreateDocking().core != null) {
                    Vector3f var11;
                    var10000 = var11 = var8.getBuildToolCreateDocking().core.getAbsolutePos(new Vector3f());
                    var10000.x -= 16.0F;
                    var11.y -= 16.0F;
                    var11.z -= 16.0F;
                    var1.getWorldTransform().transform(var11);
                    Transform var6;
                    (var6 = new Transform(var1.getWorldTransform())).origin.set(var11);
                    var8.getBuildToolCreateDocking().core.setOrientation((byte)var8.getBuildToolCreateDocking().potentialCoreOrientation);
                    Oriencube var13 = (Oriencube)var8.getBuildToolCreateDocking().core.getAlgorithm((short)662);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    Vector3f var12;
                    (var12 = new Vector3f(Element.DIRECTIONSf[var13.getOrientCubePrimaryOrientationSwitchedLeftRight()])).scale(0.5F);
                    var1.getWorldTransform().basis.transform(var12);
                    var6.origin.add(var12);
                    this.drawOrientationArrow(var6, var13.getOrientCubeSecondaryOrientation());
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }
                } else if (var5 != null && var4) {
                    int var10 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation();
                    this.drawOrientationArrow(var5, var10);
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                if (this.isDrawPreview() && !var8.isInCreateDockingMode() && this.drawToBuildBox(var1, this.drawer, ShaderLibrary.selectionShader, this.selectionShader, var3) != null && this.getPlayerIntercationManager().getSelectedTypeWithSub() == 1009 && VoidElementManager.isUsingReactorDistance()) {
                    this.drawReactorDistance(var1, ElementCollection.getIndex(this.toBuildPos));
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                this.mesh.loadVBO(true);
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                GlUtil.glDisable(2884);
                if ((Boolean)EngineSettings.G_BASIC_SELECTION_BOX.getCurrentState()) {
                    ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
                    ShaderLibrary.selectionShader.load();
                    this.drawToBuildBox(var1, (SingleBlockDrawer)null, ShaderLibrary.selectionShader, this.selectionShader, var3);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }
                } else {
                    ShaderLibrary.solidSelectionShader.setShaderInterface(this.selectionShaderSolid);
                    ShaderLibrary.solidSelectionShader.load();
                    this.drawToBuildBox(var1, (SingleBlockDrawer)null, ShaderLibrary.solidSelectionShader, this.selectionShaderSolid, var3);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                this.drawCreateDock(var1);
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                this.drawCameraHighlight(var1);
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
                ShaderLibrary.selectionShader.load();
                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 1.0F, 1.0F, 0.0F, 0.65F);
                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.9F, 0.6F, 0.2F, 0.65F);
                this.drawCurrentSelectedElement(var1, this.getShipControllerManager().getSegmentBuildController().getSelectedBlock());
                this.drawControlledElements(var1, this.getShipControllerManager().getSegmentBuildController().getSelectedBlock());
                GlUtil.glEnable(2884);
                ShaderLibrary.selectionShader.unload();
                this.mesh.unloadVBO(true);
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                for(SymmetryPlane symmetryPlane : var14) {

                    if ((symmetryPlane.getMode().equals(SymmetryMode.XY) || symmetryPlane.getMode().equals(SymmetryMode.XZ) || symmetryPlane.getMode().equals(SymmetryMode.YZ)) && EngineSettings.G_SHOW_SYMMETRY_PLANES.isOn()) {
                        this.drawCurrentSymetriePlanesElement(var1);
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if(symmetryPlane.inPlaceMode()) {
                        this.drawCurrentSymetriePlanesElement(var1, (Transform)null, symmetryPlane.getMode(), this.toBuildPos, (float)symmetryPlane.getExtraDist() * 0.5F);

                    }
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                if (var1.getMass() > 0.0F && var8.showCenterOfMass) {
                    this.drawCenterOfMass(var1);
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                if (var1 instanceof ManagedSegmentController && ((ManagedSegmentController)var1).getManagerContainer() instanceof ShieldContainerInterface) {
                    this.drawLocalShields((ShieldContainerInterface)((ManagedSegmentController)var1).getManagerContainer());
                }

                if (VoidElementManager.isUsingReactorDistance()) {
                    this.drawStabilizerOrientation(var1);
                    this.drawReactorCoordinateSystems(var1);
                }

                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                if (var8.isInCreateDockingMode()) {
                    this.drawCreateDockingMode(var1);
                }

                this.drawToBuildConnection(var1);
                if (this.drawDebug) {
                    GlUtil.printGlErrorCritical();
                }

                GlUtil.glPopMatrix();
            } else {
                GlUtil.glPushMatrix();
                BuildToolsManager var9;
                var4 = (var9 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).isAddMode();
                if (var9.getBuildHelper() != null) {
                    var9.getBuildHelper().draw();
                }

                if (this.isDrawPreview() && !var9.isInCreateDockingMode() && this.drawToBuildBox(var1, this.drawer, ShaderLibrary.selectionShader, this.selectionShader, var4) != null && this.getPlayerIntercationManager().getSelectedTypeWithSub() == 1009 && VoidElementManager.isUsingReactorDistance()) {
                    this.drawReactorDistance(var1, ElementCollection.getIndex(this.toBuildPos));
                }

                this.mesh.loadVBO(true);
                GlUtil.glDisable(2884);
                if ((Boolean)EngineSettings.G_BASIC_SELECTION_BOX.getCurrentState()) {
                    ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
                    ShaderLibrary.selectionShader.load();
                    GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.7F, 0.77F, 0.1F, 0.65F);
                    var5 = this.drawToBuildBox(var1, (SingleBlockDrawer)null, ShaderLibrary.selectionShader, this.selectionShader, var4);
                } else {
                    ShaderLibrary.solidSelectionShader.setShaderInterface(this.selectionShaderSolid);
                    ShaderLibrary.solidSelectionShader.load();
                    GlUtil.updateShaderVector4f(ShaderLibrary.solidSelectionShader, "selectionColor", 0.7F, 0.77F, 0.1F, 0.65F);
                    var5 = this.drawToBuildBox(var1, (SingleBlockDrawer)null, ShaderLibrary.solidSelectionShader, this.selectionShaderSolid, var4);
                }

                this.drawCreateDock(var1);
                this.drawCameraHighlight(var1);
                ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
                ShaderLibrary.selectionShader.load();
                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.9F, 0.6F, 0.2F, 0.65F);
                this.drawCurrentSelectedElement(var1, this.getSegmentControlManager().getSegmentBuildController().getSelectedBlock());
                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.4F, 0.1F, 0.9F, 0.65F);
                this.drawControlledElements(var1, this.getSegmentControlManager().getSegmentBuildController().getSelectedBlock());
                GlUtil.glEnable(2884);
                ShaderLibrary.selectionShader.unload();
                this.mesh.unloadVBO(true);

                for(SymmetryPlane symmetryPlane : var14) {
                    if(symmetryPlane.getMode().equals(SymmetryMode.XY) || symmetryPlane.getMode().equals(SymmetryMode.XZ) || symmetryPlane.getMode().equals(SymmetryMode.YZ)) {
                        this.drawCurrentSymetriePlanesElement(var1);
                        this.drawCurrentSymetriePlanesElement(var1, (Transform) null, symmetryPlane.getMode(), this.toBuildPos, (float) symmetryPlane.getExtraDist() * 0.5F);
                    }
                }
                if (var1 instanceof ManagedSegmentController && ((ManagedSegmentController)var1).getManagerContainer() instanceof ShieldContainerInterface) {
                    this.drawLocalShields((ShieldContainerInterface)((ManagedSegmentController)var1).getManagerContainer());
                }

                if (VoidElementManager.isUsingReactorDistance()) {
                    this.drawStabilizerOrientation(var1);
                    this.drawReactorCoordinateSystems(var1);
                }

                if (var9.isInCreateDockingMode() && var9.getBuildToolCreateDocking().core != null) {
                    Vector3f var16;
                    var10000 = var16 = var9.getBuildToolCreateDocking().core.getAbsolutePos(new Vector3f());
                    var10000.x -= 16.0F;
                    var16.y -= 16.0F;
                    var16.z -= 16.0F;
                    var1.getWorldTransform().transform(var16);
                    Transform var18;
                    (var18 = new Transform(var1.getWorldTransform())).origin.set(var16);
                    var9.getBuildToolCreateDocking().core.setOrientation((byte)var9.getBuildToolCreateDocking().potentialCoreOrientation);
                    Oriencube var17 = (Oriencube)var9.getBuildToolCreateDocking().core.getAlgorithm((short)662);
                    Vector3f var7;
                    (var7 = new Vector3f(Element.DIRECTIONSf[var17.getOrientCubePrimaryOrientationSwitchedLeftRight()])).scale(0.5F);
                    this.getSegmentControlManager().getEntered().getSegmentController().getWorldTransform().basis.transform(var7);
                    var18.origin.add(var7);
                    this.drawOrientationArrow(var18, var17.getOrientCubeSecondaryOrientation());
                } else if (var5 != null) {
                    int var15 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation();
                    this.drawOrientationArrow(var5, var15);
                }

                GlUtil.glPopMatrix();
            }

            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            inReactorAlignSlider = false;
        }
    }

    private void drawPowerHull(SegmentController var1, boolean var2) {
        if (this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().reactorHull) {
            if (var2) {
                GL11.glPolygonMode(1032, 6913);
            }

            GlUtil.glPushMatrix();
            GlUtil.glEnable(2896);
            GlUtil.glEnable(3042);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            GlUtil.glDisable(2903);
            GlUtil.glDisable(2929);
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 0.8F);
            if (var1 != null && var1 instanceof ManagedSegmentController) {
                GlUtil.glMultMatrix(var1.getWorldTransformOnClient());
                Iterator var6 = ((ManagedSegmentController)var1).getManagerContainer().getPowerInterface().getMainReactors().iterator();

                label33:
                while(true) {
                    MainReactorUnit var7;
                    do {
                        if (!var6.hasNext()) {
                            break label33;
                        }
                    } while((var7 = (MainReactorUnit)var6.next()).tris == null);

                    GlUtil.glPushMatrix();
                    GL11.glBegin(4);
                    GlUtil.glColor4f(0.4F, 0.4F, 0.3F, 0.5F);

                    for(int var3 = 0; var3 < var7.tris.length; ++var3) {
                        Triangle var4;
                        Vector3f var5 = (var4 = var7.tris[var3]).getNormal();
                        GL11.glVertex3f(var4.v1.x - 16.0F, var4.v1.y - 16.0F, var4.v1.z - 16.0F);
                        GL11.glNormal3f(var5.x, var5.y, var5.z);
                        GL11.glVertex3f(var4.v2.x - 16.0F, var4.v2.y - 16.0F, var4.v2.z - 16.0F);
                        GL11.glNormal3f(var5.x, var5.y, var5.z);
                        GL11.glVertex3f(var4.v3.x - 16.0F, var4.v3.y - 16.0F, var4.v3.z - 16.0F);
                        GL11.glNormal3f(var5.x, var5.y, var5.z);
                    }

                    GL11.glEnd();
                    GlUtil.glPopMatrix();
                }
            }

            GlUtil.glEnable(2929);
            GlUtil.glDisable(3042);
            GlUtil.glDisable(2896);
            GlUtil.glPopMatrix();
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPolygonMode(1032, 6914);
        }
    }

    private void drawCreateDockingMode(SegmentController var1) {
    }

    public boolean isInvisible() {
        return false;
    }

    public void onInit() {
        this.mesh = (Mesh)Controller.getResLoader().getMesh("Box").getChilds().get(0);
        this.selectionShader = new SelectionShader(this.mesh.getMaterial().getTexture().getTextureId());
        this.selectionShaderSolid = new SelectionShader(-1);
        this.firstDraw = false;
    }

    private void drawCenterOfMass(SegmentController var1) {
        if (var1.getPhysicsDataContainer().getShape() != null) {
            DebugLine[] var3 = var1.getCenterOfMassCross();

            for(int var2 = 0; var2 < var3.length; ++var2) {
                var3[var2].draw();
            }
        }

    }

    public void drawBlock(long var1, SegmentController var3, LinearTimerUtil var4) {
        this.pTmp.set((float)(ElementCollection.getPosX(var1) - 16), (float)(ElementCollection.getPosY(var1) - 16), (float)(ElementCollection.getPosZ(var1) - 16));
        this.dist.set((float)(ElementCollection.getPosX(var1) - 16), (float)(ElementCollection.getPosY(var1) - 16), (float)(ElementCollection.getPosZ(var1) - 16));
        var3.getWorldTransform().transform(this.dist);
        if (Controller.getCamera().isPointInFrustrum(this.dist)) {
            this.dist.sub(Controller.getCamera().getWorldTransform().origin);
            if (this.dist.length() < 64.0F) {
                GlUtil.glPushMatrix();
                GlUtil.translateModelview(this.pTmp.x, this.pTmp.y, this.pTmp.z);
                float var10001 = var4.getTime();
                GlUtil.scaleModelview(1.05F + var10001 * 0.05F, 1.05F + var10001 * 0.05F, 1.05F + var10001 * 0.05F);
                this.mesh.renderVBO();
                GlUtil.glPopMatrix();
            }
        }

    }

    public void drawBlock(Vector3i var1, SegmentController var2, LinearTimerUtil var3) {
        if (var1 != null) {
            this.pTmp.set((float)(var1.x - 16), (float)(var1.y - 16), (float)(var1.z - 16));
            GlUtil.glPushMatrix();
            GlUtil.translateModelview(this.pTmp.x, this.pTmp.y, this.pTmp.z);
            float var10001 = var3.getTime();
            GlUtil.scaleModelview(1.05F + var10001 * 0.05F, 1.05F + var10001 * 0.05F, 1.05F + var10001 * 0.05F);
            this.mesh.renderVBO();
            GlUtil.glPopMatrix();
        }

    }

    public void drawCharacterExternalMode() {
        if (this.firstDraw) {
            this.onInit();
        }

        if (this.blockOrientation != this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation()) {
            if (this.blockOrientation >= 0) {
                this.blockChangedTime = System.currentTimeMillis();
            }

            this.blockOrientation = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation();
        }

        SegmentPiece var1;
        if ((var1 = this.getPlayerManager().getSelectedBlock()) != null) {
            GlUtil.glPushMatrix();
            this.mesh.loadVBO(true);
            ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
            ShaderLibrary.selectionShader.load();
            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.9F, 0.6F, 0.2F, 0.65F);
            this.drawCurrentSelectedElement(var1.getSegment().getSegmentController(), var1);
            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.4F, 0.1F, 0.9F, 0.65F);
            this.drawControlledElements(var1.getSegment().getSegmentController(), var1);
            ShaderLibrary.selectionShader.unload();
            this.mesh.unloadVBO(true);
            GlUtil.glPopMatrix();
        }

        if (this.state.getCharacter() != null && System.currentTimeMillis() - this.blockChangedTime < 3000L) {
            this.drawOrientationArrow(this.state.getCharacter().getWorldTransform(), this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBlockOrientation());
        }

    }

    public void drawLocalShields(ShieldContainerInterface var1) {
        if (currentPiece != null && (currentPiece.getType() == 3 || currentPiece.getType() == 478)) {
            var1.getShieldAddOn().getShieldLocalAddOn().markDrawCollectionByBlock(currentPiece.getAbsoluteIndex());
        }

        if (this.getPlayerIntercationManager().getSelectedTypeWithSub() == 3 || this.getPlayerIntercationManager().getSelectedTypeWithSub() == 478) {
            if (!var1.getSegmentController().railController.isDocked()) {
                List var2 = var1.getShieldAddOn().getShieldLocalAddOn().getActiveShields();
                List var3 = var1.getShieldAddOn().getShieldLocalAddOn().getInactiveShields();
                if (!var2.isEmpty() || !var3.isEmpty()) {
                    GlUtil.glDisable(3553);
                    GlUtil.glDisable(2896);
                    GlUtil.glEnable(2903);
                    GlUtil.glEnable(3042);
                    GlUtil.glBlendFunc(770, 1);
                    Mesh var4 = (Mesh)Controller.getResLoader().getMesh("Sphere").getChilds().get(0);
                    GlUtil.glPushMatrix();
                    this.t.set(var1.getSegmentController().getWorldTransform());
                    GlUtil.glMultMatrix(this.t);
                    Vector4f var5 = new Vector4f(1.0F, 1.0F, 1.0F, 0.1F);
                    Vector4f var6 = new Vector4f(1.0F, 0.5F, 0.5F, 0.2F);
                    Vector4f var7 = new Vector4f(1.0F, 1.0F, 0.2F, 0.6F);
                    Vector4f var8 = new Vector4f(1.0F, 0.2F, 1.0F, 0.6F);
                    Vector4f var9 = new Vector4f(1.0F, 0.6F, 1.0F, 0.9F);
                    Iterator var11 = var2.iterator();

                    ShieldLocal var10;
                    while(var11.hasNext()) {
                        var10 = (ShieldLocal)var11.next();
                        GlUtil.glPushMatrix();
                        this.drawLocalShield(var1, var10, var5, var7, var8, var9, var4);
                        GlUtil.glPopMatrix();
                    }

                    var11 = var3.iterator();

                    while(var11.hasNext()) {
                        var10 = (ShieldLocal)var11.next();
                        GlUtil.glPushMatrix();
                        this.drawLocalShield(var1, var10, var6, var7, var8, var9, var4);
                        GlUtil.glPopMatrix();
                    }

                    GlUtil.glPopMatrix();
                    GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlUtil.glDisable(3042);
                }

            }
        }
    }

    private void drawLocalShield(ShieldContainerInterface var1, ShieldLocal var2, Vector4f var3, Vector4f var4, Vector4f var5, Vector4f var6, Mesh var7) {
        int var15 = ElementCollection.getPosX(var2.outputPos) - 16;
        int var8 = ElementCollection.getPosY(var2.outputPos) - 16;
        int var9 = ElementCollection.getPosZ(var2.outputPos) - 16;
        drawPoint((SegmentController)null, (float)var15, (float)var8, (float)var9, var4, true);
        if (var2.active) {
            Iterator var14 = var2.supportCoMIds.iterator();

            while(var14.hasNext()) {
                long var12;
                int var10 = ElementCollection.getPosX(var12 = (Long)var14.next()) - 16;
                int var11 = ElementCollection.getPosY(var12) - 16;
                int var16 = ElementCollection.getPosZ(var12) - 16;
                drawPoint((SegmentController)null, (float)var10, (float)var11, (float)var16, var5, true);
                GlUtil.glDisable(2929);
                drawArrow((float)var10, (float)var11, (float)var16, (float)var15, (float)var8, (float)var9, var1.getSegmentController().getWorldTransformOnClient(), var6);
                GlUtil.glEnable(2929);
            }
        }

        GlUtil.glColor4f(var3);
        GlUtil.translateModelview((float)var15, (float)var8, (float)var9);
        GlUtil.glDepthMask(false);
        if (var2.isPositionInRadiusWorld(var1.getSegmentController().getWorldTransformOnClient(), Controller.getCamera().getPos())) {
            GL11.glCullFace(1028);
        } else {
            GL11.glCullFace(1029);
        }

        GlUtil.drawSphere(var2.radius, 20.0F);
        GL11.glPolygonMode(1032, 6913);
        GlUtil.drawSphere(var2.radius, 20.0F);
        GL11.glPolygonMode(1032, 6914);
        GlUtil.glDepthMask(true);
        GL11.glCullFace(1029);
    }

    private static void drawArrow(float var0, float var1, float var2, float var3, float var4, float var5, Transform var6, Vector4f var7) {
        DebugLine[] var8 = DebugLine.getArrow(new Vector3f(var0, var1, var2), new Vector3f(var3, var4, var5), var7, 0.25F, 3.0F, 0.5F, 50.0F, var6);

        for(int var9 = 0; var9 < var8.length; ++var9) {
            var8[var9].drawRaw();
        }

    }

    private static void drawPoint(SegmentController var0, float var1, float var2, float var3, Vector4f var4, boolean var5) {
        DebugLine[] var6 = DebugLine.getCross((Transform)(var0 != null ? var0.getWorldTransformOnClient() : TransformTools.ident), new Vector3f(var1, var2, var3), 1.5F, 1.5F, 1.5F, var5);

        for(int var7 = 0; var7 < var6.length; ++var7) {
            DebugLine var8;
            (var8 = var6[var7]).setColor(var4);
            var8.drawRaw();
        }

    }

    public void drawStabilizerOrientation(SegmentController var1) {
        if (VoidElementManager.STABILIZER_BONUS_CALC != StabBonusCalcStyle.BY_ANGLE) {
            ManagerContainer var2;
            if (var1 instanceof ManagedSegmentController && (var2 = ((ManagedSegmentController)var1).getManagerContainer()).hasActiveReactors() && var2.getStabilizer().getElementCollections().size() > 0) {
                GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlUtil.glDisable(3553);
                GlUtil.glDisable(2896);
                GlUtil.glEnable(3042);
                GlUtil.glBlendFunc(770, 771);
                GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
                GlUtil.glEnable(2903);
                GlUtil.glDisable(2884);
                GlUtil.glPushMatrix();
                GlUtil.glMultMatrix(var1.getWorldTransformOnClient());

                for(int var3 = 0; var3 < var2.getStabilizer().getElementCollections().size(); ++var3) {
                    StabilizerUnit var4;
                    int var5;
                    if ((var5 = (var4 = (StabilizerUnit)var2.getStabilizer().getElementCollections().get(var3)).getReactorSide()) >= 0) {
                        if (currentPiece != null && var4.getNeighboringCollection().contains(currentPiece.getAbsoluteIndex())) {
                            this.currentSelectedStabSide = var5;
                        }

                        float var6 = (float)var4.getCoMOrigin().x - 16.0F;
                        float var7 = (float)var4.getCoMOrigin().y - 16.0F;
                        float var8 = (float)var4.getCoMOrigin().z - 16.0F;
                        this.tmp.set(Element.DIRECTIONSf[var5]);
                        this.tmp.scale(10.0F);
                        var2.getPowerInterface().getActiveReactor().getBonusMatrix().transform(this.tmp);
                        Vector3f var10000 = this.tmp;
                        var10000.x += var6;
                        var10000 = this.tmp;
                        var10000.y += var7;
                        var10000 = this.tmp;
                        var10000.z += var8;
                        if (var4.isBonusSlot()) {
                            this.stabColor.set(Element.SIDE_COLORS[var5]);
                            Vector4f var9 = this.stabColor;
                            var9.x += this.colorMod.getTime();
                            var9 = this.stabColor;
                            var9.y += this.colorMod.getTime();
                            var9 = this.stabColor;
                            var9.z += this.colorMod.getTime();
                            this.stabColor.w = 0.9F;
                        } else {
                            this.stabColor.set(0.5F, 0.5F, 0.5F, 0.5F);
                        }

                        GlUtil.glDisable(2929);
                        drawArrow(var6, var7, var8, this.tmp.x, this.tmp.y, this.tmp.z, var1.getWorldTransformOnClient(), this.stabColor);
                        GlUtil.glEnable(2929);
                    }
                }

                GlUtil.glDisable(3042);
                GlUtil.glEnable(2884);
                GlUtil.glPopMatrix();
                GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

        }
    }

    public void drawReactorDistance(SegmentController var1, long var2) {
        ManagerContainer var4;
        if (var1 instanceof ManagedSegmentController && (var4 = ((ManagedSegmentController)var1).getManagerContainer()).isUsingPowerReactors() && var4.getPowerInterface().getMainReactors().size() > 0) {
            PowerInterface var12 = var4.getPowerInterface();
            Vector3f var5 = new Vector3f();
            Vector3f var6 = new Vector3f();
            ElementCollection.getPosFromIndex(var2, var6);
            float var7 = (float) (1.0F / 0.0);
            Iterator var9 = var12.getMainReactors().iterator();

            while(var9.hasNext()) {
                float var8;
                if ((var8 = ((MainReactorUnit)var9.next()).distanceToThis(var2, this.v)) < var7) {
                    var7 = var8;
                    var5.set(this.v.outFrom);
                }
            }

            this.t.set(var1.getWorldTransform());
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(this.t);
            GlUtil.glDisable(2896);
            GlUtil.glEnable(3042);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            GlUtil.glEnable(2903);
            GlUtil.glColor4f(0.5F, 1.0F, 0.5F, 0.9F);
            GlUtil.glDisable(2884);
            GL11.glLineWidth(2.5F);
            double var14 = var12.getReactorOptimalDistance();
            currentStabDist = var7;
            currentOptStabDist = (float)var14;
            currentStabEfficiency = var12.calcStabilization(var14, var7);
            if ((double)var7 < var14) {
                GlUtil.glColor4f(1.0F, 0.27F, 0.34F, 0.9F);
            }

            GL11.glBegin(1);
            GL11.glVertex3f(var5.x - 16.0F, var5.y - 16.0F, var5.z - 16.0F);
            GL11.glVertex3f(var6.x - 16.0F, var6.y - 16.0F, var6.z - 16.0F);
            if ((double)var7 < var14) {
                Vector3f var13;
                (var13 = new Vector3f()).sub(var6, var5);
                float var11 = (float)(var14 - (double)var7);
                var13.normalize();
                var13.scale(var11);
                var13.add(var6);
                GlUtil.glColor4f(0.5F, 0.5F, 1.0F, 0.9F);
                GL11.glVertex3f(var6.x - 16.0F, var6.y - 16.0F, var6.z - 16.0F);
                GL11.glVertex3f(var13.x - 16.0F, var13.y - 16.0F, var13.z - 16.0F);
            }

            GL11.glEnd();
            GlUtil.glDisable(3042);
            GlUtil.glEnable(2884);
            GlUtil.glPopMatrix();
        }

    }

    public void drawControlledElements(SegmentController var1, SegmentPiece var2) {
        if (var2 != null) {
            this.conDrw.clear();
            Vector4f var3 = new Vector4f(0.4F, 0.1F, 0.9F, 0.65F);
            Vector4f var4 = new Vector4f(var3);

            try {
                long var5 = -9223372036854775808L;
                if (currentPiece != null) {
                    var5 = currentPiece.getAbsoluteIndex();
                }

                Vector3i var7 = var2.getAbsolutePos(this.posTmp);
                long var8 = -9223372036854775808L;
                long var10 = -9223372036854775808L;
                long var12 = -9223372036854775808L;
                ManagerModuleCollection var14;
                UsableControllableElementManager var16;
                ControlBlockElementCollectionManager var17;
                Iterator var19;
                if (var1 instanceof ManagedSegmentController && (var14 = ((ManagedSegmentController)var1).getManagerContainer().getModulesControllerMap().get(var2.getType())) != null && (var16 = var14.getElementManager()) instanceof UsableControllableElementManager && (var17 = (ControlBlockElementCollectionManager)((UsableControllableElementManager)var16).getCollectionManagersMap().get(ElementCollection.getIndex(var7))) != null) {
                    var17.drawnUpdateNumber = this.state.getNumberOfUpdate();
                    var8 = var17.getSlaveConnectedElement();
                    var10 = var17.getEffectConnectedElement();
                    var12 = var17.getLightConnectedElement();
                    var19 = var17.getElementCollections().iterator();

                    while(var19.hasNext()) {
                        ElementCollection var15;
                        if ((var15 = (ElementCollection)var19.next()).contains(var5)) {
                            currentPieceIndexIntegrity = var5;
                            currentPieceIntegrity = var15.getIntegrity();

                            for(int var21 = 0; var21 < 7; ++var21) {
                                this.touching.append("Touch " + var21 + "/6: " + var15.touching[var21] + "; x" + VoidElementManager.getIntegrityBaseTouching(var21) + " -> " + Math.round((double)var15.touching[var21] * VoidElementManager.getIntegrityBaseTouching(var21)) + "\n");
                            }
                        }

                        var15.markDraw();
                        var15.setDrawColor(var3.x + 1.0F / (1.0F - var3.x) * this.linearTimerSl.getTime() * 0.5F, var3.y + 1.0F / (1.0F - var3.y) * this.linearTimerSl.getTime() * 0.5F, var3.z + 1.0F / (1.0F - var3.z) * this.linearTimerSl.getTime() * 0.5F, var3.w);
                        this.conDrw.add(var17.getEnhancerClazz());
                    }
                }

                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", var4);
                PositionControl var20;
                if (EngineSettings.G_DRAW_SELECTED_BLOCK_WOBBLE.isOn() && var2 != null && (var20 = var1.getControlElementMap().getDirectControlledElements((short)32767, var7)) != null) {
                    this.prepareBlockDraw(var1.getWorldTransform());
                    var19 = var20.getControlMap().iterator();

                    while(true) {
                        long var22;
                        do {
                            if (!var19.hasNext()) {
                                this.endBlockDraw();
                                return;
                            }

                            var22 = (Long)var19.next();
                        } while(EngineSettings.F_FRAME_BUFFER.isOn() && !EngineSettings.G_DRAW_SELECTED_BLOCK_WOBBLE_ALWAYS.isOn() && this.conDrw.contains((short)ElementCollection.getType(var22)));

                        if (var22 == var8) {
                            var4.set(0.1F, 0.5F, 0.8F, 0.65F);
                            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", var4);
                        }

                        if (var22 == var10) {
                            var4.set(0.1F, 0.9F, 0.1F, 0.65F);
                            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", var4);
                        }

                        if (var22 == var12) {
                            var4.set(0.6F, 0.9F, 0.2F, 0.65F);
                            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", var4);
                        }

                        this.drawBlock(var22, var1, this.linearTimerC);
                        if (!var4.equals(var3)) {
                            var4.set(var3);
                            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", var4);
                        }
                    }
                }
            } catch (ConcurrentModificationException var18) {
                var18.printStackTrace();
            }
        }
    }

    public void drawCurrentCamElement(SegmentController var1) {
        if (Controller.getCamera() instanceof BuildShipCamera) {
            this.t.set(var1.getWorldTransform());
            Vector3f var2 = ((BuildShipCamera)Controller.getCamera()).getRelativeCubePos();
            this.t.basis.transform(var2);
            this.t.origin.add(var2);
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(this.t);
            GlUtil.scaleModelview(1.01F, 1.01F, 1.01F);
            GlUtil.glDisable(2896);
            GlUtil.glEnable(3042);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            GlUtil.glEnable(2903);
            GlUtil.glColor4f(0.0F, 0.0F, 1.0F, 0.6F);
            GlUtil.scaleModelview(0.1F, 0.1F, 0.1F);
            this.mesh.renderVBO();
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlUtil.glEnable(2896);
            GlUtil.glDisable(2903);
            GlUtil.glDisable(3042);
            GlUtil.glPopMatrix();
        }

    }

    public void drawCurrentSelectedElement(SegmentController var1, SegmentPiece var2) {
        if (var2 != null) {
            this.prepareBlockDraw(var1.getWorldTransform());
            var2.refresh();
            Vector3i var3 = var2.getAbsolutePos(this.posTmp);
            this.drawBlock(var3, var1, this.linearTimer);
            this.endBlockDraw();
        }

    }

    public void drawCurrentSymetriePlanesElement(SegmentController var1) {
        ArrayList<SymmetryPlane> symmetryPlanes = this.getActiveBuildController().getAllSymmetryPlanes();
        for(SymmetryPlane symmetryPlane : symmetryPlanes) {
            GlUtil.glEnable(2929);
            GlUtil.glDisable(2896);
            GlUtil.glEnable(3042);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            GlUtil.glEnable(2903);
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 0.7F);
            GlUtil.glDisable(2884);
            this.t.set(var1.getWorldTransform());
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(this.t);
            GlUtil.glPopMatrix();
            GlUtil.glBindTexture(3553, 0);
            GlUtil.glEnable(2884);
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlUtil.glEnable(2896);
            GlUtil.glDisable(2903);
            GlUtil.glDisable(3042);
            float var4;
            var4 = (float) symmetryPlane.getExtraDist() * 0.5F;
            this.drawCurrentSymetriePlanesElement(var1, (Transform) null, symmetryPlane.getMode(), symmetryPlane.getPlane(), var4);
        }
    }

    private void drawReactorCoordinateSystems(SegmentController var1) {
        PowerInterface var2;
        if (var1 instanceof ManagedSegmentController && (var2 = ((ManagedSegmentController)var1).getManagerContainer().getPowerInterface()).getActiveReactor() != null) {
            this.drawReactorCoordinateSystem(var1, var2.getActiveReactor());
        }

    }

    private void drawReactorCoordinateSystem(SegmentController var1, ReactorTree var2) {
        Transform var3;
        (var3 = new Transform()).setIdentity();
        var3.basis.set(var2.getBonusMatrix());
        Vector3i var13 = ElementCollection.getPosFromIndex(var2.getCenterOfMass(), new Vector3i());
        Vector3f var4 = new Vector3f(-1.0F, -1.0F, -1.0F);
        Vector3f var5 = new Vector3f(1.0F, 1.0F, 1.0F);
        this.t.set(var1.getWorldTransform());
        Vector4f var6 = new Vector4f(Element.SIDE_COLORS[1]);
        Vector4f var7 = new Vector4f(Element.SIDE_COLORS[0]);
        Vector4f var8 = new Vector4f(Element.SIDE_COLORS[2]);
        Vector4f var9 = new Vector4f(Element.SIDE_COLORS[3]);
        Vector4f var10 = new Vector4f(Element.SIDE_COLORS[5]);
        Vector4f var11 = new Vector4f(Element.SIDE_COLORS[4]);
        float var12 = inReactorAlignSlider ? 0.2F : 0.0F;
        if (inReactorAlignAlwaysVisible) {
            var12 = 0.3F;
        }

        var6.w = var12;
        var7.w = var12;
        var8.w = var12;
        var9.w = var12;
        var10.w = var12;
        var11.w = var12;
        Vector4f var14 = null;
        switch(this.currentSelectedStabSide) {
            case 0:
                var14 = var7;
                break;
            case 1:
                var14 = var6;
                break;
            case 2:
                var14 = var8;
                break;
            case 3:
                var14 = var9;
                break;
            case 4:
                var14 = var11;
                break;
            case 5:
                var14 = var10;
        }

        if (var14 != null) {
            var6.w = 0.05F;
            var7.w = 0.05F;
            var8.w = 0.05F;
            var9.w = 0.05F;
            var10.w = 0.05F;
            var11.w = 0.05F;
            var14.x += this.colorMod.getTime();
            var14.y += this.colorMod.getTime();
            var14.z += this.colorMod.getTime();
            var14.w = 0.3F + this.colorMod.getTime() * 0.3F;
            this.drawReactoAlignCross(var3, var13, var4, var5, 0.0F);
        } else if (inReactorAlignAlwaysVisible) {
            this.drawReactoAlignCross(var3, var13, var4, var5, 0.0F);
        } else if (inReactorAlignSliderSelectedAxis >= 0) {
            this.drawReactoAlignCross(var3, var13, var4, var5, 0.0F);
        }

        this.drawCurrentSymetriePlanesElement(var1, var3, SymmetryMode.XY , var13, 0.0F, var6, var7, var4, var5);
        this.drawCurrentSymetriePlanesElement(var1, var3, SymmetryMode.XZ, var13, 0.0F, var8, var9, var4, var5);
        this.drawCurrentSymetriePlanesElement(var1, var3, SymmetryMode.YZ, var13, 0.0F, var10, var11, var4, var5);
        inReactorAlignSliderSelectedAxis = -1;
    }

    private void drawReactoAlignCross(Transform var1, Vector3i var2, Vector3f var3, Vector3f var4, float var5) {
        GlUtil.glPushMatrix();
        GlUtil.glMultMatrix(this.t);
        GlUtil.glTranslatef((float)(var2.x - 16) + var5, (float)(var2.y - 16) + var5, (float)(var2.z - 16) + var5);
        GlUtil.glMultMatrix(var1);
        GlUtil.glDisable(3553);
        GlUtil.glEnable(2929);
        GlUtil.glDisable(2896);
        GlUtil.glEnable(3042);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        GlUtil.glEnable(2903);
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 0.6F);
        GlUtil.glDisable(2884);
        GL11.glBegin(1);
        if (inReactorAlignSliderSelectedAxis < 0 || inReactorAlignSliderSelectedAxis == 4) {
            GL11.glVertex3f(var3.x * 32.0F + var5, 0.0F, 0.0F);
            GL11.glVertex3f(var4.x * 32.0F + var5, 0.0F, 0.0F);
        }

        if (inReactorAlignSliderSelectedAxis < 0 || inReactorAlignSliderSelectedAxis == 2) {
            GL11.glVertex3f(0.0F, var3.y * 32.0F + var5, 0.0F);
            GL11.glVertex3f(0.0F, var4.y * 32.0F + var5, 0.0F);
        }

        if (inReactorAlignSliderSelectedAxis < 0 || inReactorAlignSliderSelectedAxis == 1) {
            GL11.glVertex3f(0.0F, 0.0F, var3.z * 32.0F + var5);
            GL11.glVertex3f(0.0F, 0.0F, var4.z * 32.0F + var5);
        }

        GL11.glEnd();
        GlUtil.glPopMatrix();
    }

    private void drawCurrentSymetriePlanesElement(SegmentController var1, Transform var2, SymmetryMode var3, Vector3i var4, float var5) {
        Vector3f var6 = new Vector3f((float)(var1.getMinPos().x - 1), (float)(var1.getMinPos().y - 1), (float)(var1.getMinPos().z - 1));
        Vector3f var7 = new Vector3f((float)(var1.getMaxPos().x + 1), (float)(var1.getMaxPos().y + 1), (float)(var1.getMaxPos().z + 1));
        Vector4f var8 = new Vector4f();
        Vector4f var9 = new Vector4f();
        if (var3.equals(SymmetryMode.XY)) {
            var8.set(0.0F, 0.0F, 1.0F, 0.3F);
        } else if (var3.equals(SymmetryMode.XZ)) {
            var8.set(0.0F, 1.0F, 0.0F, 0.3F);
        } else if (var3.equals(SymmetryMode.YZ)) {
            var8.set(1.0F, 0.0F, 0.0F, 0.3F);
        }

        var9.set(var8);
        this.drawCurrentSymetriePlanesElement(var1, var2, var3, var4, var5, var8, var9, var6, var7);
    }

    private void drawCurrentSymetriePlanesElement(SegmentController var1, Transform var2, SymmetryMode var3, Vector3i var4, float var5, Vector4f var6, Vector4f var7, Vector3f var8, Vector3f var9) {
        GlUtil.glDepthMask(false);
        GlUtil.glEnable(2929);
        GlUtil.glEnable(3553);
        GlUtil.glDisable(2896);
        GlUtil.glEnable(3042);
        GlUtil.glBlendFunc(770, 1);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        GlUtil.glEnable(2903);
        GlUtil.glEnable(2884);
        Controller.getResLoader().getSprite("symm-plane").getMaterial().getTexture().attach(0);
        Vector3f var10 = new Vector3f(var9.x - var8.x, var9.y - var8.y, var9.z - var8.z);
        this.t.set(var1.getWorldTransform());
        GlUtil.glPushMatrix();
        GlUtil.glMultMatrix(this.t);
        GlUtil.glTranslatef((float)(var4.x - 16) + var5, (float)(var4.y - 16) + var5, (float)(var4.z - 16) + var5);
        if (var2 != null) {
            GlUtil.glMultMatrix(var2);
        }

        float var11 = var8.x * 32.0F;
        float var12 = var9.x * 32.0F;
        float var13 = var8.y * 32.0F;
        var5 = var9.y * 32.0F;
        float var14 = var8.z * 32.0F;
        float var15 = var9.z * 32.0F;
        switch (var3) {
            case XY:
                GL11.glBegin(7);
                GlUtil.glColor4f(var6);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(var11, var13, 0.0F);
                GL11.glTexCoord2f(0.0F, var10.y / 0.07F);
                GL11.glVertex3f(var11, var5, 0.0F);
                GL11.glTexCoord2f(var10.x / 0.07F, var10.y / 0.07F);
                GL11.glVertex3f(var12, var5, 0.0F);
                GL11.glTexCoord2f(var10.x / 0.07F, 0.0F);
                GL11.glVertex3f(var12, var13, 0.0F);
                GlUtil.glColor4f(var7);
                GL11.glTexCoord2f(var10.x / 0.07F, 0.0F);
                GL11.glVertex3f(var12, var13, 0.0F);
                GL11.glTexCoord2f(var10.x / 0.07F, var10.y / 0.07F);
                GL11.glVertex3f(var12, var5, 0.0F);
                GL11.glTexCoord2f(0.0F, var10.y / 0.07F);
                GL11.glVertex3f(var11, var5, 0.0F);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(var11, var13, 0.0F);
                GL11.glEnd();
                break;
            case XZ:
                GL11.glBegin(7);
                GlUtil.glColor4f(var6);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(var11, 0.0F, var14);
                GL11.glTexCoord2f(0.0F, var10.z / 0.07F);
                GL11.glVertex3f(var11, 0.0F, var15);
                GL11.glTexCoord2f(var10.x / 0.07F, var10.z / 0.07F);
                GL11.glVertex3f(var12, 0.0F, var15);
                GL11.glTexCoord2f(var10.x / 0.07F, 0.0F);
                GL11.glVertex3f(var12, 0.0F, var14);
                GlUtil.glColor4f(var7);
                GL11.glTexCoord2f(var10.x / 0.07F, 0.0F);
                GL11.glVertex3f(var12, 0.0F, var14);
                GL11.glTexCoord2f(var10.x / 0.07F, var10.z / 0.07F);
                GL11.glVertex3f(var12, 0.0F, var15);
                GL11.glTexCoord2f(0.0F, var10.z / 0.07F);
                GL11.glVertex3f(var11, 0.0F, var15);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(var11, 0.0F, var14);
                GL11.glEnd();
                break;
            case YZ:
                GL11.glBegin(7);
                GlUtil.glColor4f(var6);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(0.0F, var13, var14);
                GL11.glTexCoord2f(0.0F, var10.z / 0.07F);
                GL11.glVertex3f(0.0F, var13, var15);
                GL11.glTexCoord2f(var10.y / 0.07F, var10.z / 0.07F);
                GL11.glVertex3f(0.0F, var5, var15);
                GL11.glTexCoord2f(var10.y / 0.07F, 0.0F);
                GL11.glVertex3f(0.0F, var5, var14);
                GlUtil.glColor4f(var7);
                GL11.glTexCoord2f(var10.y / 0.07F, 0.0F);
                GL11.glVertex3f(0.0F, var5, var14);
                GL11.glTexCoord2f(var10.y / 0.07F, var10.z / 0.07F);
                GL11.glVertex3f(0.0F, var5, var15);
                GL11.glTexCoord2f(0.0F, var10.z / 0.07F);
                GL11.glVertex3f(0.0F, var13, var15);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex3f(0.0F, var13, var14);
                GL11.glEnd();
                break;
        }

        GlUtil.glPopMatrix();
        GlUtil.glBindTexture(3553, 0);
        GlUtil.glEnable(2884);
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlUtil.glEnable(2896);
        GlUtil.glDisable(2903);
        GlUtil.glDisable(3042);
        GlUtil.glDepthMask(true);
    }

    private void drawOrientationArrow(Transform var1, int var2) {
        this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
        GlUtil.glPushMatrix();
        Mesh var3 = (Mesh)Controller.getResLoader().getMesh("Arrow").getChilds().get(0);
        var1 = new Transform(var1);
        SegmentController.setConstraintFrameOrientation((byte)var2, var1, GlUtil.getRightVector(new Vector3f(), var1), GlUtil.getUpVector(new Vector3f(), var1), GlUtil.getForwardVector(new Vector3f(), var1));
        Vector3f var4;
        (var4 = new Vector3f(0.0F, 0.0F, 0.1F)).scale(this.linearTimer.getTime() / 5.0F);
        var4.z -= 0.3F;
        var1.basis.transform(var4);
        var1.origin.add(var4);
        GlUtil.glMultMatrix(var1);
        GlUtil.scaleModelview(0.13F, 0.13F, 0.13F);
        GlUtil.glEnable(3042);
        GlUtil.glDisable(2884);
        GlUtil.glEnable(2896);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        GlUtil.glEnable(2903);
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, this.linearTimer.getTime() - 0.5F);
        var3.draw();
        GlUtil.glPopMatrix();
        GlUtil.glDisable(2903);
        GlUtil.glDisable(3042);
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private Transform getToBuildTransform(SegmentController var1) {
        if (this.testRayCollisionPoint != null && this.testRayCollisionPoint.hasHit() && this.testRayCollisionPoint instanceof CubeRayCastResult) {
            CubeRayCastResult var2;
            if ((var2 = this.testRayCollisionPoint).getSegment() == null) {
                return null;
            } else {
                assert var1 != null;

                assert this.t != null;

                assert var1.getWorldTransform() != null;

                this.t.set(var1.getWorldTransform());
                Vector3f var3;
                Vector3f var10000 = var3 = new Vector3f((float)var2.getSegment().pos.x, (float)var2.getSegment().pos.y, (float)var2.getSegment().pos.z);
                var10000.x += (float)(var2.getCubePos().x - 16);
                var3.y += (float)(var2.getCubePos().y - 16);
                var3.z += (float)(var2.getCubePos().z - 16);
                Vector3f var10 = new Vector3f(this.testRayCollisionPoint.hitPointWorld);
                var1.getWorldTransformInverse().transform(var10);
                this.pp.set((int)var3.x, (int)var3.y, (int)var3.z);
                this.toBuildPos.set(this.pp.x + 16, this.pp.y + 16, this.pp.z + 16);
                BuildToolsManager var4 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
                Vector3f var5 = new Vector3f((float)var4.getWidth(), (float)var4.getHeight(), (float)var4.getDepth());
                if (!PlayerInteractionControlManager.isAdvancedBuildMode(this.state)) {
                    var5.set(1.0F, 1.0F, 1.0F);
                }

                Vector3f var6 = new Vector3f();
                IntOpenHashSet var7 = new IntOpenHashSet();

                for(int var8 = 0; var8 < 6; ++var8) {
                    Vector3i var9 = Element.DIRECTIONSi[var8];
                    SegmentPiece var12;
                    if ((var12 = var1.getSegmentBuffer().getPointUnsave(new Vector3i(this.toBuildPos.x + var9.x, this.toBuildPos.y + var9.y, this.toBuildPos.z + var9.z))) != null && var12.getType() != 0) {
                        var7.add(var8);
                    }
                }

                SegmentPiece var11 = var1.getSegmentBuffer().getPointUnsave(this.toBuildPos);
                int var13;
                currentSide = var13 = Element.getSide(var10, var11 == null ? null : var11.getAlgorithm(), this.pp, var11 != null ? var11.getType() : 0, var11 != null ? var11.getOrientation() : 0, var7);
                switch(var13) {
                    case 0:
                        if (var4.isAddMode()) {
                            ++var3.z;
                        }

                        ++this.toBuildPos.z;
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 1:
                        if (var4.isAddMode()) {
                            --var3.z;
                        }

                        --this.toBuildPos.z;
                        var6.set(var5.x, var5.y, -var5.z);
                        break;
                    case 2:
                        if (var4.isAddMode()) {
                            ++var3.y;
                        }

                        ++this.toBuildPos.y;
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 3:
                        if (var4.isAddMode()) {
                            --var3.y;
                        }

                        --this.toBuildPos.y;
                        var6.set(var5.x, -var5.y, var5.z);
                        break;
                    case 4:
                        if (var4.isAddMode()) {
                            ++var3.x;
                        }

                        ++this.toBuildPos.x;
                        var6.set(var5.x, var5.y, var5.z);
                        break;
                    case 5:
                        if (var4.isAddMode()) {
                            --var3.x;
                        }

                        --this.toBuildPos.x;
                        var6.set(-var5.x, var5.y, var5.z);
                }

                var3.x += var6.x / 2.0F - 0.5F * Math.signum(var6.x);
                var3.y += var6.y / 2.0F - 0.5F * Math.signum(var6.y);
                var3.z += var6.z / 2.0F - 0.5F * Math.signum(var6.z);
                Vector3f var14 = new Vector3f(var3);
                this.t.basis.transform(var14);
                this.t.origin.add(var14);
                return new Transform(this.t);
            }
        } else {
            return null;
        }
    }

    private boolean isDrawPreview() {
        BuildToolsManager var1 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
        return EngineSettings.G_PREVIEW_TO_BUILD_BLOCK.isOn() && var1.isAddMode() && !var1.isCopyMode();
    }

    private Transform drawToBuildBox(SegmentController var1, SingleBlockDrawer var2, Shader var3, SelectionShader var4, boolean var5) {
        GlUtil.glEnable(3042);
        GlUtil.glDisable(2896);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        if (this.drawDebug) {
            GlUtil.printGlErrorCritical();
        }

        if (this.testRayCollisionPoint != null && this.testRayCollisionPoint.hasHit() && this.testRayCollisionPoint instanceof CubeRayCastResult) {
            CubeRayCastResult var6;
            if ((var6 = this.testRayCollisionPoint).getSegment() == null) {
                return null;
            } else {
                assert var1 != null;

                assert this.t != null;

                assert var1.getWorldTransform() != null;

                this.t.set(var1.getWorldTransform());
                Vector3f var7;
                Vector3f var10000 = var7 = new Vector3f((float)var6.getSegment().pos.x, (float)var6.getSegment().pos.y, (float)var6.getSegment().pos.z);
                var10000.x += (float)(var6.getCubePos().x - 16);
                var7.y += (float)(var6.getCubePos().y - 16);
                var7.z += (float)(var6.getCubePos().z - 16);
                BuildToolsManager var8;
                if ((var8 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).getBuildHelper() != null && !var8.getBuildHelper().placed) {
                    var8.getBuildHelper().localTransform.origin.set(var7);
                }

                if ((!var8.isInCreateDockingMode() || var8.getBuildToolCreateDocking().docker == null) && !var8.isSelectMode()) {
                    Vector3f var9 = new Vector3f(this.testRayCollisionPoint.hitPointWorld);
                    var1.getWorldTransformInverse().transform(var9);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    this.pp.set((int)Math.floor((double)var7.x), (int)Math.floor((double)var7.y), (int)Math.floor((double)var7.z));
                    this.toBuildPos.set(this.pp.x + 16, this.pp.y + 16, this.pp.z + 16);
                    this.loockingAtPos.set(this.toBuildPos);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    Vector3f var10 = var8.getSizef();
                    if (var2 == null) {
                        if (!var5) {
                            if (var8.isCopyMode()) {
                                GlUtil.updateShaderVector4f(var3, "selectionColor", 0.6F, 0.6F, 0.04F, 1.0F);
                            } else if (var8.isPasteMode()) {
                                GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.1F, 0.5F, 1.0F);
                            } else {
                                GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.1F, 0.1F, 1.0F);
                            }

                            var10.x = -var10.x;
                            var10.y = -var10.y;
                            var10.z = -var10.z;
                        } else {
                            GlUtil.updateShaderVector4f(var3, "selectionColor", 0.6F, 0.6F, 0.04F, 1.0F);
                        }
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (!PlayerInteractionControlManager.isAdvancedBuildMode(this.state)) {
                        var10.set(1.0F, 1.0F, 1.0F);
                    }

                    float var11 = FastMath.max(Math.abs(var10.x), Math.abs(var10.y), Math.abs(var10.z));
                    Vector3f var21;
                    (var21 = new Vector3f(var6.hitPointWorld)).sub(Controller.getCamera().getWorldTransform().origin);
                    var11 = Math.min(0.1F, var11 / 100.0F) + Math.min(0.2F, var21.length() / 500.0F);
                    var21 = new Vector3f();
                    IntOpenHashSet var12 = new IntOpenHashSet();

                    SegmentPiece var15;
                    for(int var13 = 0; var13 < 6; ++var13) {
                        Vector3i var14 = Element.DIRECTIONSi[var13];
                        if ((var15 = var1.getSegmentBuffer().getPointUnsave(new Vector3i(this.toBuildPos.x + var14.x, this.toBuildPos.y + var14.y, this.toBuildPos.z + var14.z))) != null && ElementKeyMap.isValidType(var15.getType())) {
                            ElementKeyMap.getInfoFast(var15.getType());
                            var12.add(var13);
                        }
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    SegmentPiece var25 = var1.getSegmentBuffer().getPointUnsave(this.toBuildPos);
                    int var26;
                    switch(var26 = Element.getSide(var9, var25 == null ? null : var25.getAlgorithm(), this.pp, var25 != null ? var25.getType() : 0, var25 != null ? var25.getOrientation() : 0, var12)) {
                        case 0:
                            if (var5) {
                                ++var7.z;
                            }

                            ++this.toBuildPos.z;
                            var21.set(var10.x, var10.y, var10.z);
                            break;
                        case 1:
                            if (var5) {
                                --var7.z;
                            }

                            --this.toBuildPos.z;
                            var21.set(var10.x, var10.y, -var10.z);
                            break;
                        case 2:
                            if (var5) {
                                ++var7.y;
                            }

                            ++this.toBuildPos.y;
                            var21.set(var10.x, var10.y, var10.z);
                            break;
                        case 3:
                            if (var5) {
                                --var7.y;
                            }

                            --this.toBuildPos.y;
                            var21.set(var10.x, -var10.y, var10.z);
                            break;
                        case 4:
                            if (var5) {
                                ++var7.x;
                            }

                            ++this.toBuildPos.x;
                            var21.set(var10.x, var10.y, var10.z);
                            break;
                        case 5:
                            if (var5) {
                                --var7.x;
                            }

                            --this.toBuildPos.x;
                            var21.set(-var10.x, var10.y, var10.z);
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (var8.isInCreateDockingMode()) {
                        var8.getBuildToolCreateDocking().potentialCreateDockPos = null;
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    Vector3f var27;
                    if (var8.getCopyArea() != null && var8.isPasteMode()) {
                        var3.unload();
                        this.mesh.unloadVBO(true);
                        var27 = new Vector3f(var7);
                        Transform var22;
                        (var22 = new Transform(this.t)).basis.transform(var27);
                        var22.origin.add(var27);
                        GlUtil.glPushMatrix();
                        GlUtil.glMultMatrix(var22);
                        var8.getCopyArea().draw();
                        GlUtil.glPopMatrix();
                        this.mesh.loadVBO(true);
                        var3.setShaderInterface(var4);
                        var3.load();
                        if (var2 == null) {
                            if (!var5) {
                                if (var8.isCopyMode()) {
                                    GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.8F, 0.2F, 1.0F);
                                } else if (var8.isPasteMode()) {
                                    GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.1F, 0.5F, 1.0F);
                                } else {
                                    GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.1F, 0.1F, 1.0F);
                                }
                            } else {
                                GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.77F, 0.1F, 1.0F);
                            }
                        }
                    }

                    GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (var8.isInCreateDockingMode()) {
                        GlUtil.updateShaderVector4f(var3, "selectionColor", 0.7F, 0.1F, 0.1F, 1.0F);
                        if (var8.getBuildToolCreateDocking().docker == null) {
                            if ((var15 = var1.getSegmentBuffer().getPointUnsave(this.loockingAtPos)) != null && ElementKeyMap.isValidType(var15.getType()) && ElementKeyMap.getInfoFast(var15.getType()).isRailDockable()) {
                                Oriencube var28 = (Oriencube)BlockShapeAlgorithm.getAlgo(ElementKeyMap.getInfoFast(var15.getType()).getBlockStyle(), var15.getOrientation());
                                var1 = null;
                                if (Element.switchLeftRight(var28.getOrientCubePrimaryOrientation()) == var26) {
                                    GlUtil.updateShaderVector4f(var3, "selectionColor", 0.1F, 0.8F, 0.1F, 1.0F);
                                    var8.getBuildToolCreateDocking().potentialCreateDockPos = new VoidUniqueSegmentPiece(var15);
                                }
                            }
                        } else {
                            GlUtil.updateShaderVector4f(var3, "selectionColor", 0.1F, 0.1F, 0.9F, 1.0F);
                        }
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    Vector3f var19;
                    if ((Boolean)EngineSettings.G_BASIC_SELECTION_BOX.getCurrentState()) {
                        var27 = new Vector3f(var21);
                        var9 = new Vector3f(var7);
                        Transform var16 = new Transform(this.t);
                        var9.x += var27.x / 2.0F - 0.5F * Math.signum(var27.x);
                        var9.y += var27.y / 2.0F - 0.5F * Math.signum(var27.y);
                        var9.z += var27.z / 2.0F - 0.5F * Math.signum(var27.z);
                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        var19 = new Vector3f(var9);
                        var16.basis.transform(var19);
                        var16.origin.add(var19);
                        GlUtil.glPushMatrix();
                        GlUtil.glMultMatrix(var16);
                        if (var5) {
                            var27.scale(0.99993F);
                        } else {
                            var27.scale(1.00003F);
                        }

                        if (var2 == null) {
                            GlUtil.scaleModelview(var27.x, var27.y, var27.z);
                        }

                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        if (var2 == null) {
                            this.mesh.renderVBO();
                        }

                        GlUtil.glPopMatrix();
                    } else {
                        var7.x -= var11 / 2.0F * Math.signum(var21.x);
                        var7.y -= var11 / 2.0F * Math.signum(var21.y);
                        var7.z -= var11 / 2.0F * Math.signum(var21.z);

                        for(int var23 = 0; var23 < 12; ++var23) {
                            Vector3f var17 = new Vector3f(var21);
                            var19 = new Vector3f(var7);
                            Transform var20 = new Transform(this.t);
                            switch(var23) {
                                case 0:
                                    var19.x += var11 * Math.signum(var17.x);
                                    var17.x -= var11 * Math.signum(var17.x);
                                    var17.y = var11 * Math.signum(var17.y);
                                    var17.z = var11 * Math.signum(var17.z);
                                    break;
                                case 1:
                                    var17.x -= var11 * Math.signum(var17.x);
                                    var17.y = var11 * Math.signum(var17.y);
                                    var17.z = var11 * Math.signum(var17.z);
                                    break;
                                case 2:
                                    var19.x += var11 * Math.signum(var17.x);
                                    var17.x -= var11 * Math.signum(var17.x);
                                    var17.y = var11 * Math.signum(var17.y);
                                    var17.z = var11 * Math.signum(var17.z);
                                    break;
                                case 3:
                                    var19.x += var11 * Math.signum(var17.x);
                                    var17.x -= var11 * Math.signum(var17.x);
                                    var17.y = var11 * Math.signum(var17.y);
                                    var17.z = var11 * Math.signum(var17.z);
                                    break;
                                case 4:
                                case 5:
                                case 6:
                                    var19.z += var11 * Math.signum(var17.z);
                                case 7:
                                    var17.z -= var11 * Math.signum(var17.z);
                                    var17.y = var11 * Math.signum(var17.y);
                                    var17.x = var11 * Math.signum(var17.x);
                                    break;
                                case 8:
                                case 9:
                                case 10:
                                case 11:
                                    var17.y += var11 * Math.signum(var17.y);
                                    var17.x = var11 * Math.signum(var17.x);
                                    var17.z = var11 * Math.signum(var17.z);
                            }

                            var19.x += var17.x / 2.0F - 0.5F * Math.signum(var17.x);
                            var19.y += var17.y / 2.0F - 0.5F * Math.signum(var17.y);
                            var19.z += var17.z / 2.0F - 0.5F * Math.signum(var17.z);
                            if (var23 == 1) {
                                var19.y += var21.y;
                                var19.x += var11 * Math.signum(var17.x);
                            } else if (var23 == 2) {
                                var19.z += var21.z;
                            } else if (var23 == 3) {
                                var19.y += var21.y;
                                var19.z += var21.z;
                            } else if (var23 == 5) {
                                var19.x += var21.x;
                            } else if (var23 == 6) {
                                var19.y += var21.y;
                            } else if (var23 == 7) {
                                var19.x += var21.x;
                                var19.y += var21.y;
                                var19.z += var11 * Math.signum(var17.z);
                            } else if (var23 == 9) {
                                var19.x += var21.x;
                            } else if (var23 == 10) {
                                var19.z += var21.z;
                            } else if (var23 == 11) {
                                var19.x += var21.x;
                                var19.z += var21.z;
                            }

                            if (this.drawDebug) {
                                GlUtil.printGlErrorCritical();
                            }

                            var19 = new Vector3f(var19);
                            var20.basis.transform(var19);
                            var20.origin.add(var19);
                            GlUtil.glPushMatrix();
                            GlUtil.glMultMatrix(var20);
                            if (var5) {
                                var17.scale(0.99993F);
                            } else {
                                var17.scale(1.00003F);
                            }

                            if (var2 == null) {
                                GlUtil.scaleModelview(var17.x, var17.y, var17.z);
                            }

                            if (this.drawDebug) {
                                GlUtil.printGlErrorCritical();
                            }

                            if (this.drawDebug) {
                                GlUtil.printGlErrorCritical();
                            }

                            if (var2 == null) {
                                this.mesh.renderVBO();
                            }

                            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                            GlUtil.glPopMatrix();
                        }

                        var7.x += var11 / 2.0F * Math.signum(var21.x);
                        var7.y += var11 / 2.0F * Math.signum(var21.y);
                        var7.z += var11 / 2.0F * Math.signum(var21.z);
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    var7.x += var21.x / 2.0F - 0.5F * Math.signum(var21.x);
                    var7.y += var21.y / 2.0F - 0.5F * Math.signum(var21.y);
                    var7.z += var21.z / 2.0F - 0.5F * Math.signum(var21.z);
                    var27 = new Vector3f(var7);
                    this.t.basis.transform(var27);
                    this.t.origin.add(var27);
                    GlUtil.glPushMatrix();
                    GlUtil.glMultMatrix(this.t);
                    if (var5) {
                        var21.scale(0.99993F);
                    } else {
                        var21.scale(1.00003F);
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    if (var2 != null && var5) {
                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        boolean var24 = this.mesh.isVboLoaded();
                        if (this.mesh.isVboLoaded()) {
                            this.mesh.unloadVBO(true);
                        }

                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        short var18;
                        if (ElementKeyMap.isValidType(var18 = this.getPlayerIntercationManager().getSelectedTypeWithSub())) {
                            var2.alpha = 0.5F;
                            if (ElementKeyMap.getInfo(var18).getBlockStyle() != BlockStyle.NORMAL) {
                                var2.setSidedOrientation((byte)0);
                                var2.setShapeOrientation24((byte)this.getPlayerIntercationManager().getBlockOrientation());
                            } else if (ElementKeyMap.getInfo(var18).getIndividualSides() > 3) {
                                var2.setShapeOrientation24((byte)0);
                                var2.setSidedOrientation((byte)this.getPlayerIntercationManager().getBlockOrientation());
                            } else if (ElementKeyMap.getInfo(var18).orientatable) {
                                var2.setShapeOrientation24((byte)0);
                                var2.setSidedOrientation((byte)this.getPlayerIntercationManager().getBlockOrientation());
                            } else {
                                var2.setShapeOrientation24((byte)0);
                                var2.setSidedOrientation((byte)0);
                            }

                            if (this.drawDebug) {
                                GlUtil.printGlErrorCritical();
                            }

                            var2.activateBlinkingOrientation(ElementKeyMap.getInfo(var18).isOrientatable());
                            if (this.drawDebug) {
                                GlUtil.printGlErrorCritical();
                            }

                            GL11.glCullFace(1029);
                            var2.useSpriteIcons = false;
                            var2.drawType(var18, this.t);
                            var2.useSpriteIcons = true;
                        }

                        if (this.drawDebug) {
                            GlUtil.printGlErrorCritical();
                        }

                        if (var24) {
                            this.mesh.loadVBO(true);
                        }
                    }

                    GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlUtil.glEnable(2896);
                    GlUtil.glDisable(3042);
                    GL11.glCullFace(1029);
                    GlUtil.glDisable(2884);
                    GlUtil.glPopMatrix();
                    return new Transform(this.t);
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private void drawCameraHighlight(SegmentController var1) {
        BuildToolsManager var2;
        if ((var2 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).isSelectMode()) {
            var2.getSelectMode().draw(this.state, var1, this.mesh, this.selectionShader);
        }

    }

    private void drawCreateDock(SegmentController var1) {
        BuildToolsManager var2;
        if ((var2 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager()).isInCreateDockingMode() && var2.getBuildToolCreateDocking().docker != null) {
            VoidUniqueSegmentPiece var3 = var2.getBuildToolCreateDocking().docker;
            GlUtil.glEnable(3042);
            GlUtil.glDisable(2896);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            Transform var4 = new Transform(var1.getWorldTransform());
            Vector3f var5;
            Vector3f var10000 = var5 = var3.getAbsolutePos(new Vector3f());
            var10000.x -= 16.0F;
            var5.y -= 16.0F;
            var5.z -= 16.0F;
            var4.basis.transform(var5);
            var4.origin.add(var5);
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(var4);
            if (this.drawer == null) {
                GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.1F, 0.3F, 0.9F, 0.65F);
                this.mesh.renderVBO();
            } else {
                boolean var6 = this.mesh.isVboLoaded();
                if (this.mesh.isVboLoaded()) {
                    this.mesh.unloadVBO(true);
                }

                this.drawer.alpha = 0.5F;
                this.drawer.setSidedOrientation((byte)0);
                BlockShapeAlgorithm.getLocalAlgoIndex(ElementKeyMap.getInfo(var3.getType()).getBlockStyle(), var3.getOrientation());
                this.drawer.setShapeOrientation24(var3.getOrientation());
                this.drawer.useSpriteIcons = false;
                this.drawer.drawType(var2.getBuildToolCreateDocking().docker.getType(), var4);
                this.drawer.useSpriteIcons = true;
                if (var6) {
                    this.mesh.loadVBO(true);
                }

                this.drawer.setActive(false);
            }

            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlUtil.glEnable(2896);
            GlUtil.glDisable(3042);
            GlUtil.glPopMatrix();
            Vector3f var9;
            if (var2.getBuildToolCreateDocking().core != null) {
                var3 = var2.getBuildToolCreateDocking().core;
            } else {
                (var3 = new VoidUniqueSegmentPiece()).uniqueIdentifierSegmentController = var1.getUniqueIdentifier();
                var3.setType((short)1);
                var5 = new Vector3f(Controller.getCamera().getPos());
                (var9 = Controller.getCamera().getForward(new Vector3f())).scale(var2.getBuildToolCreateDocking().coreDistance);
                var5.add(var9);
                var1.getWorldTransformInverse().transform(var5);
                var5.x = (float)(Math.round(var5.x) + 16);
                var5.y = (float)(Math.round(var5.y) + 16);
                var5.z = (float)(Math.round(var5.z) + 16);
                var3.voidPos.set(new Vector3i(var5));
                SegmentPiece var7;
                if (((var7 = var1.getSegmentBuffer().getPointUnsave(var3.voidPos)) == null || !ElementKeyMap.isValidType(var7.getType())) && !var3.voidPos.equals(var2.getBuildToolCreateDocking().docker.voidPos)) {
                    var3.setSegmentController(var1);
                    var2.getBuildToolCreateDocking().potentialCore = var3;
                } else {
                    var2.getBuildToolCreateDocking().potentialCore = null;
                }
            }

            GlUtil.glEnable(3042);
            GlUtil.glDisable(2896);
            GlUtil.glBlendFunc(770, 771);
            GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
            Transform var8 = new Transform(var1.getWorldTransform());
            var10000 = var9 = var3.getAbsolutePos(new Vector3f());
            var10000.x -= 16.0F;
            var9.y -= 16.0F;
            var9.z -= 16.0F;
            var8.basis.transform(var9);
            var8.origin.add(var9);
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(var8);
            ShaderLibrary.selectionShader.setShaderInterface(this.selectionShader);
            ShaderLibrary.selectionShader.load();
            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.1F, 0.9F, 0.6F, 0.65F);
            this.mesh.renderVBO();
            GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlUtil.glEnable(2896);
            GlUtil.glDisable(3042);
            GlUtil.glPopMatrix();
        }

    }

    private Transform drawToBuildConnection(SegmentController var1) {
        if (this.testRayCollisionPoint != null && this.testRayCollisionPoint.hasHit() && this.testRayCollisionPoint instanceof CubeRayCastResult) {
            CubeRayCastResult var2;
            if ((var2 = this.testRayCollisionPoint).getSegment() == null) {
                return null;
            } else if (PlayerInteractionControlManager.isAdvancedBuildMode(this.state)) {
                return null;
            } else {
                this.t.set(var1.getWorldTransform());
                Vector3f var3;
                Vector3f var10000 = var3 = new Vector3f((float)var2.getSegment().pos.x, (float)var2.getSegment().pos.y, (float)var2.getSegment().pos.z);
                var10000.x += (float)(var2.getCubePos().x - 16);
                var3.y += (float)(var2.getCubePos().y - 16);
                var3.z += (float)(var2.getCubePos().z - 16);
                short var10 = this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedTypeWithSub();
                Vector3f var4 = new Vector3f();
                SegmentPiece var5;
                if ((var5 = this.getActiveBuildController().getSelectedBlock()) != null && var5.getType() != 0 && var10 != 0) {
                    Vector3i var6 = var5.getAbsolutePos(new Vector3i());
                    var4.set((float)(var6.x - 16), (float)(var6.y - 16), (float)(var6.z - 16));
                    Vector3f var11 = new Vector3f(this.testRayCollisionPoint.hitPointWorld);
                    var1.getWorldTransformInverse().transform(var11);
                    new Vector3f();
                    IntOpenHashSet var7 = new IntOpenHashSet();

                    for(int var8 = 0; var8 < 6; ++var8) {
                        Vector3i var9 = Element.DIRECTIONSi[var8];
                        SegmentPiece var13;
                        if ((var13 = var1.getSegmentBuffer().getPointUnsave(new Vector3i(this.toBuildPos.x + var9.x, this.toBuildPos.y + var9.y, this.toBuildPos.z + var9.z))) != null && var13.getType() != 0) {
                            var7.add(var8);
                        }
                    }

                    SegmentPiece var12;
                    if ((var12 = var1.getSegmentBuffer().getPointUnsave(this.toBuildPos)) != null) {
                        switch(Element.getSide(var11, var12 == null ? null : var12.getAlgorithm(), this.pp, var12 != null ? var12.getType() : 0, var12 != null ? var12.getOrientation() : 0, var7)) {
                            case 0:
                                ++var3.z;
                                break;
                            case 1:
                                --var3.z;
                                break;
                            case 2:
                                ++var3.y;
                                break;
                            case 3:
                                --var3.y;
                                break;
                            case 4:
                                ++var3.x;
                                break;
                            case 5:
                                --var3.x;
                        }
                    }

                    this.pp.set((int)var3.x, (int)var3.y, (int)var3.z);
                    GlUtil.glPushMatrix();
                    GlUtil.glMultMatrix(this.t);
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    GlUtil.glDisable(3553);
                    GlUtil.glEnable(2903);
                    GlUtil.glDisable(2896);
                    GlUtil.glEnable(3042);
                    GlUtil.glBlendFunc(770, 771);
                    GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
                    GL11.glLineWidth(4.0F);
                    if (ElementKeyMap.isValidType(var10) && ElementKeyMap.getInfo(var10).getControlledBy().contains(var5.getType())) {
                        GlUtil.glColor4f(0.0F, 0.8F, 0.0F, 1.0F);
                    } else {
                        GlUtil.glColor4f(0.8F, 0.0F, 0.0F, 0.6F);
                    }

                    GL11.glBegin(1);
                    GL11.glVertex3f(var4.x, var4.y, var4.z);
                    GL11.glVertex3f(var3.x, var3.y, var3.z);
                    GL11.glEnd();
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    GL11.glLineWidth(2.0F);
                    GlUtil.glDisable(3042);
                    GlUtil.glDisable(2903);
                    GlUtil.glEnable(2896);
                    GlUtil.glEnable(3553);
                    GlUtil.glPopMatrix();
                    if (this.drawDebug) {
                        GlUtil.printGlErrorCritical();
                    }

                    return new Transform(this.t);
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private void endBlockDraw() {
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlUtil.glEnable(2896);
        GlUtil.glDisable(2903);
        GlUtil.glDisable(3042);
        GlUtil.glPopMatrix();
    }

    public void flagControllerSetChanged() {
    }

    public void flagUpdate() {
        this.flagUpdate = true;
    }

    public SegmentBuildController getActiveBuildController() {
        if (this.getSegmentControlManager().getSegmentBuildController().isTreeActive()) {
            return this.getSegmentControlManager().getSegmentBuildController();
        } else {
            return this.getShipControllerManager().getSegmentBuildController().isTreeActive() ? this.getShipControllerManager().getSegmentBuildController() : null;
        }
    }

    public PlayerInteractionControlManager getPlayerIntercationManager() {
        return this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
    }

    public PlayerExternalController getPlayerManager() {
        return this.getPlayerIntercationManager().getPlayerCharacterManager();
    }

    public SegmentControlManager getSegmentControlManager() {
        return this.getPlayerIntercationManager().getSegmentControlManager();
    }

    public ShipControllerManager getShipControllerManager() {
        return this.getPlayerIntercationManager().getInShipControlManager().getShipControlManager();
    }

    private void prepareBlockDraw(Transform var1) {
        this.t.set(var1);
        GlUtil.glEnable(3042);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        GlUtil.glDisable(2896);
        GlUtil.glEnable(2903);
        GlUtil.glColor4f(1.0F, 0.0F, 1.0F, 0.6F);
        GlUtil.glPushMatrix();
        GlUtil.glMultMatrix(var1);
    }

    private void textPopups() {
    }

    public void update(Timer var1) {
        HudIndicatorOverlay.toDrawTexts.remove(this.indication);
        if (this.getSegmentControlManager().getSegmentBuildController().isTreeActive() || this.getShipControllerManager().getSegmentBuildController().isTreeActive() || this.getPlayerManager().isActive()) {
            if (this.state.getCharacter() != null) {
                if (this.flagUpdate) {
                    this.lastSegment = null;
                    currentPiece = null;
                    currentInfo = null;
                    this.flagUpdate = false;
                }

                this.colorMod.update(var1);
                this.linearTimer.update(var1);
                this.linearTimerC.update(var1);
                this.linearTimerSl.update(var1);

                try {
                    SegmentBuildController var9 = this.getActiveBuildController();
                    Vector3f var2 = new Vector3f(Controller.getCamera().getPos());
                    if (var9 == null && this.state.getCharacter() == this.state.getCurrentPlayerObject()) {
                        var2.set(this.state.getCharacter().getHeadWorldTransform().origin);
                    }

                    Vector3f var3;
                    if (!Float.isNaN((var3 = new Vector3f(Controller.getCamera().getForward())).x)) {
                        Vector3f var4;
                        if (PlayerInteractionControlManager.isAdvancedBuildMode(this.state)) {
                            var4 = new Vector3f(this.state.getWorldDrawer().getAbsoluteMousePosition());
                            var3.sub(var4, var2);
                        }

                        var3.normalize();
                        var3.scale(var9 != null ? 300.0F : 6.0F);
                        (var4 = new Vector3f(var2)).add(var3);
                        PlayerCharacter var5 = this.state.getCharacter();
                        SegmentController var10000 = this.state.getCurrentPlayerObject() instanceof SegmentController ? (SegmentController)this.state.getCurrentPlayerObject() : null;
                        SegmentController var6 = var10000;
                        if (var10000 != null) {
                            this.testRayCollisionPoint = new CubeRayCastResult(var2, var4, var5, new SegmentController[]{var6});
                        } else {
                            this.testRayCollisionPoint = new CubeRayCastResult(var2, var4, var5, new SegmentController[0]);
                        }

                        this.testRayCollisionPoint.setDamageTest(false);
                        this.testRayCollisionPoint.setIgnoereNotPhysical(true);
                        this.testRayCollisionPoint.setIgnoreDebris(true);
                        this.testRayCollisionPoint.setZeroHpPhysical(true);
                        this.testRayCollisionPoint.setCheckStabilizerPaths(false);
                        this.testRayCollisionPoint.setHasCollidingBlockFilter(false);
                        this.testRayCollisionPoint.setCollidingBlocks((Int2ObjectOpenHashMap)null);
                        ((ModifiedDynamicsWorld)((PhysicsExt)this.state.getPhysics()).getDynamicsWorld()).rayTest(var2, var4, this.testRayCollisionPoint);
                        if (this.testRayCollisionPoint.collisionObject != null && !(this.testRayCollisionPoint.collisionObject instanceof RigidBodySegmentController)) {
                            this.testRayCollisionPoint.setSegment((Segment)null);
                        }

                        if (this.testRayCollisionPoint != null && this.testRayCollisionPoint.hasHit() && this.testRayCollisionPoint instanceof CubeRayCastResult) {
                            CubeRayCastResult var10;
                            if ((var10 = this.testRayCollisionPoint).collisionObject instanceof PairCachingGhostObjectAlignable) {
                                this.currentObject = ((PairCachingGhostObjectAlignable)var10.collisionObject).getObj();
                                currentPiece = null;
                                currentInfo = null;
                            } else if (var10.getSegment() != null) {
                                if (var10.getSegment() != null && this.lastSegment != null && !var10.getSegment().equals(this.lastSegment) || !var10.getCubePos().equals(this.lastCubePos) || currentPiece == null) {
                                    this.lastCubePos.set(var10.getCubePos());
                                    this.lastSegment = var10.getSegment();
                                    this.currentObject = this.lastSegment.getSegmentController();
                                    currentInfo = ElementKeyMap.getInfo((currentPiece = new SegmentPiece(this.lastSegment, this.lastCubePos)).getType());
                                }
                            } else {
                                currentPiece = null;
                                currentInfo = null;
                                this.currentObject = null;
                            }
                        } else {
                            this.currentObject = null;
                            currentPiece = null;
                            currentInfo = null;
                        }

                        if (currentInfo != null) {
                            if (currentInfo.isArmor()) {
                                this.retrieveArmorInfo(currentPiece.getSegmentController(), currentPiece, new Vector3f(var2), new Vector3f(var3));
                            } else {
                                armorValue.reset();
                            }

                            if (Keyboard.isKeyDown(54) && Controller.getCamera().getCameraOffset() < 1.0F) {
                                this.indication.setText(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_VIEW_BUILDMODEDRAWER_0, new Object[]{currentInfo.getId(), currentPiece, currentPiece.getSegmentController().getUniqueIdentifier(), StringTools.formatPointZero(currentPiece.getSegmentController().railController.calculateRailMassIncludingSelf()), this.touching.toString(), currentPiece.getSegmentController().isUsingOldPower() ? "[OLD POWER]" : "[NEW POWER]"}));
                                currentPiece.getTransform(this.indication.getCurrentTransform());
                                HudIndicatorOverlay.toDrawTexts.add(this.indication);
                            } else if (this.state.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager().buildInfo) {
                                this.indication.setText(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_VIEW_BUILDMODEDRAWER_1, new Object[]{currentInfo.getName(), Element.getSideString(currentPiece.getOrientation()), currentPiece.getAbsolutePos(new Vector3i()).toString(), currentPiece.getHitpointsFull(), currentInfo.getMaxHitPointsFull()}));
                                currentPiece.getTransform(this.indication.getCurrentTransform());
                                HudIndicatorOverlay.toDrawTexts.add(this.indication);
                            }
                        } else if (this.currentObject != null && this.currentObject instanceof AbstractCharacter && this.currentObject instanceof AICreature) {
                            this.indication.setText(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_VIEW_BUILDMODEDRAWER_27, new Object[]{KeyboardMappings.ACTIVATE.getKeyChar(), ((AICreature)this.currentObject).getRealName()}));
                            this.indication.getCurrentTransform().set(this.currentObject.getWorldTransformOnClient());
                            HudIndicatorOverlay.toDrawTexts.add(this.indication);
                        }

                        if (currentPiece == null) {
                            armorValue.reset();
                        }

                        if (var9 != null) {
                            label127: {
                                if (var9.getSelectedBlock() != null) {
                                    var9.getSelectedBlock().refresh();
                                    if (selectedBlock != var9.getSelectedBlock() && var9.getSelectedBlock().getType() != 0) {
                                        selectedInfo = ElementKeyMap.getInfo((selectedBlock = var9.getSelectedBlock()).getType());
                                        break label127;
                                    }

                                    if (var9.getSelectedBlock().getType() != 0) {
                                        break label127;
                                    }
                                }

                                selectedBlock = null;
                                selectedInfo = null;
                            }

                            short var11;
                            if (selectedBlock != null && (var11 = selectedBlock.getType()) != 0 && currentInfo != null) {
                                if (selectedInfo != null) {
                                    try {
                                        currentInfo.getControlledBy().contains(var11);
                                    } catch (ElementClassNotFoundException var7) {
                                        var7.printStackTrace();
                                    }
                                }

                                currentInfo.isController();
                            }

                            if (selectedInfo != null) {
                                ElementInformation var12 = currentInfo;
                                var12 = selectedInfo;
                            }

                            this.textPopups();
                        }
                    }
                } catch (Exception var8) {
                    var8.printStackTrace();
                    System.err.println("[BUILDMODEDRAWER] " + var8.getClass().getSimpleName() + ": " + var8.getMessage());
                }
            }
        }
    }

    private void retrieveArmorInfo(SegmentController var1, SegmentPiece var2, Vector3f var3, Vector3f var4) {
        Vector3f var5 = new Vector3f(var3);
        var4.normalize();
        var4.scale(400.0F);
        var5.add(var4);
        if (!this.cPosA.epsilonEquals(var3, 0.1F) || !this.cPosB.epsilonEquals(var5, 4.0F) || this.state.getUpdateTime() - this.lastArmorCheck >= 1000L) {
            this.lastArmorCheck = this.state.getUpdateTime();
            this.cPosA.set(var3);
            this.cPosB.set(var5);
            this.rayCallbackTraverse.closestHitFraction = 1.0F;
            this.rayCallbackTraverse.collisionObject = null;
            this.rayCallbackTraverse.setSegment((Segment)null);
            this.rayCallbackTraverse.rayFromWorld.set(var3);
            this.rayCallbackTraverse.rayToWorld.set(var5);
            this.rayCallbackTraverse.setFilter(new SegmentController[]{var1});
            this.rayCallbackTraverse.setOwner(this.state.getCharacter());
            this.rayCallbackTraverse.setIgnoereNotPhysical(false);
            this.rayCallbackTraverse.setIgnoreDebris(false);
            this.rayCallbackTraverse.setRecordAllBlocks(false);
            this.rayCallbackTraverse.setZeroHpPhysical(false);
            this.rayCallbackTraverse.setDamageTest(true);
            this.rayCallbackTraverse.setCheckStabilizerPaths(false);
            this.rayCallbackTraverse.setSimpleRayTest(true);
            this.pt.armorValue = armorValue;
            armorValue.reset();
            ((ModifiedDynamicsWorld)var1.getPhysics().getDynamicsWorld()).rayTest(var3, var5, this.rayCallbackTraverse);
            if (armorValue.typesHit.size() > 0) {
                armorValue.calculate();
            }

            this.rayCallbackTraverse.collisionObject = null;
            this.rayCallbackTraverse.setSegment((Segment)null);
            this.rayCallbackTraverse.setFilter(new SegmentController[0]);
        }
    }
}