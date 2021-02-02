package org.schema.game.client.controller.manager.ingame;

import api.listener.events.block.SegmentPieceActivateByPlayer;
import api.mod.StarLoader;
import com.bulletphysics.collision.dispatch.CollisionWorld.ClosestRayResultCallback;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.thederpgamer.betterbuilding.BetterBuilding;
import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryMode;
import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.FastMath;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.*;
import org.schema.game.client.controller.element.world.ClientSegmentProvider;
import org.schema.game.client.controller.manager.AbstractControlManager;
import org.schema.game.client.controller.manager.ingame.character.PlayerExternalController;
import org.schema.game.client.controller.manager.ingame.faction.FactionBlockDialog;
import org.schema.game.client.controller.manager.ingame.navigation.NavigationControllerManager;
import org.schema.game.client.controller.manager.ingame.ship.InShipControlManager;
import org.schema.game.client.controller.tutorial.states.PlaceElementOnLastSpawnedTestState;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.BuildModeDrawer;
import org.schema.game.client.view.buildhelper.BuildHelper;
import org.schema.game.client.view.camera.ObjectViewerCamera;
import org.schema.game.client.view.cubes.shapes.BlockShapeAlgorithm;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.client.view.cubes.shapes.orientcube.Oriencube;
import org.schema.game.client.view.gui.RadialMenuDialogShapes;
import org.schema.game.client.view.gui.buildtools.BuildConstructionCommand;
import org.schema.game.client.view.gui.buildtools.BuildConstructionManager;
import org.schema.game.client.view.gui.buildtools.GUIOrientationSettingElement;
import org.schema.game.client.view.gui.reactor.ReactorTreeDialog;
import org.schema.game.client.view.textbox.Replacements;
import org.schema.game.common.controller.*;
import org.schema.game.common.controller.ai.AIGameConfiguration;
import org.schema.game.common.controller.ai.AiInterfaceContainer;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.controller.ai.UnloadedAiEntityException;
import org.schema.game.common.controller.elements.*;
import org.schema.game.common.controller.elements.dockingBlock.DockingBlockCollectionManager;
import org.schema.game.common.controller.elements.lift.LiftCollectionManager;
import org.schema.game.common.controller.elements.power.PowerAddOn;
import org.schema.game.common.controller.elements.power.PowerManagerInterface;
import org.schema.game.common.controller.elements.powerbattery.PowerBatteryUnit;
import org.schema.game.common.controller.elements.transporter.TransporterCollectionManager;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.VoidUniqueSegmentPiece;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.element.*;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.player.PlayerControlledTransformableNotFound;
import org.schema.game.common.data.player.SimplePlayerCommands;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventoryHolder;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.network.objects.DragDrop;
import org.schema.game.network.objects.remote.RemoteDragDrop;
import org.schema.game.network.objects.remote.RemoteTextBlockPair;
import org.schema.game.network.objects.remote.TextBlockPair;
import org.schema.game.server.data.GameServerState;
import org.schema.schine.common.TextCallback;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.viewer.FixedViewer;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.core.settings.PrefixNotFoundException;
import org.schema.schine.graphicsengine.core.settings.StateParameterNotFoundException;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.graphicsengine.forms.font.FontLibrary.FontSize;
import org.schema.schine.graphicsengine.forms.font.FontStyle;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextButton;
import org.schema.schine.graphicsengine.forms.gui.newgui.DialogInterface;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.graphicsengine.util.WorldToScreenConverter;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;
import org.schema.schine.network.objects.Sendable;

import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PlayerInteractionControlManager extends AbstractControlManager {
    public static final long MENU_DELAY_MS = 200L;
    private final List<BuildInstruction> undo = new ObjectArrayList(30);
    private final List<BuildInstruction> redo = new ObjectArrayList(30);
    private final IntOpenHashSet warned = new IntOpenHashSet();
    private final Object2IntOpenHashMap<BlockStyle> blockStyleMap = new Object2IntOpenHashMap();
    private int selectedSlot;
    private HashMap<Integer, Integer> slotKeyMap;
    private InShipControlManager inShipControlManager;
    private SegmentControlManager segmentControlManager;
    private PlayerExternalController playerCharacterManager;
    private SimpleTransformableSendableObject selectedEntity;
    private BuildToolsManager buildToolsManager;
    private HotbarLayout hotbarLayout;
    private int blockOrientation = 0;
    private short lastSelectedType;
    private AiInterfaceContainer selectedCrew;
    private String lastPowerMsg;
    private String lastShieldMsg;
    private SegmentPiece entered;
    private int selectedSubSlot;
    private boolean firstWarning;
    private float undoRedoCooldown = -1.0F;
    private Vector3i mPos = new Vector3i();
    private Camera lastCamera;
    private Camera lookCamera;
    private int lastSelectedSlabOrient;
    private Set<Segment> moddedSegs = new ObjectOpenHashSet(16);
    private short forcedSelect;
    private long lastPressedAdvBuildModeButton;
    private boolean stickyAdvBuildMode;
    private boolean stacked;
    private SimpleTransformableSendableObject selectedAITarget;
    private boolean hasNotMadePin;
    private final BuildConstructionManager buildCommandManager;
    private static final Transform where = new Transform();

    public PlayerInteractionControlManager(GameClientState var1) {
        super(var1);
        this.initialize();
        this.buildCommandManager = new BuildConstructionManager(var1);
    }

    public void addNetworkInputKeyEvent(KeyEventInterface var1) {
        if (!this.isSuspended() && this.isActive()) {
            assert this.getState().getController().getPlayerInputs().isEmpty();

            this.getState().getPlayer().getNetworkObject().handleKeyEvent(KeyboardMappings.getEventKeyState(var1, this.getState()), KeyboardMappings.getEventKeyRaw(var1));
        }

    }

    public short checkCanBuild(final EditableSendableSegmentController var1, SymmetryPlanes var2, short var3) {
        if (!this.getState().getController().allowedToEdit(var1)) {
            return 0;
        } else if (var1.isInTestSector()) {
            this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_65, 0.0F);
            return 0;
        } else if (!this.isAllowedToBuildAndSpawnShips()) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_0, 0.0F);
            return 0;
        } else if (this.getState().getController().getTutorialMode() == null || !(this.getState().getController().getTutorialMode().getMachine().getFsm().getCurrentState() instanceof PlaceElementOnLastSpawnedTestState) || var3 != 16 || this.getInShipControlManager().getShipControlManager().getSegmentBuildController().getSelectedBlock() != null && this.getInShipControlManager().getShipControlManager().getSegmentBuildController().getSelectedBlock().getType() == 6) {
            this.stacked = false;
            if (var3 == -32768) {
                List var4;
                if ((var4 = this.getState().getPlayer().getInventory().getSubSlots(this.getSelectedSlot())) == null || var4.isEmpty()) {
                    return 0;
                }

                var3 = ((InventorySlot)var4.get(this.getSelectedSubSlot() % var4.size())).getType();
                System.err.println("[CLIENT] PLACING MULTISLOT: " + this.getSelectedSubSlot() + " -> " + var3);
            } else if (ElementKeyMap.isValidType(var3) && ElementKeyMap.getInfoFast(var3).blocktypeIds != null) {
                short var10;
                if (this.forcedSelect != 0) {
                    var10 = this.getForcedSelect();
                    this.stacked = true;
                } else {
                    var10 = this.getState().getBlockSyleSubSlotController().getBlockType(var3, this.getSelectedSubSlot());
                    this.stacked = true;
                }

                if (var10 < 0) {
                    System.err.println("[CLIENT] POPUP RADIAL");
                    this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().checkRadialSelect(var3);
                    return 0;
                }

                var3 = var10;
            }

            if (var3 < 0 && !BetterBuilding.getInstance().currentPlane.inPlaceMode()) {
                this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_2, 0.0F);
                return 0;
            } else if (var1.getHpController().isRebooting()) {
                this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_3, new Object[]{StringTools.formatTimeFromMS(var1.getHpController().getRebootTimeLeftMS())}), 0.0F);
                return 0;
            } else {
                if (var1.isScrap()) {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_10, 0.0F);
                    if (this.firstWarning) {
                        this.firstWarning = false;
                        return 0;
                    }
                }

                if (!this.buildToolsManager.isInCreateDockingMode()) {
                    if (var1.hasStructureAndArmorHP() && var1.getHpController().getHpPercent() < 1.0D && !this.warned.contains(var1.getId())) {
                        this.warned.add(var1.getId());
                        PlayerGameOkCancelInput var14;
                        (var14 = new PlayerGameOkCancelInput("CONFIRM", this.getState(), Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_61, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_8) {
                            public boolean isOccluded() {
                                return false;
                            }

                            public void onDeactivate() {
                            }

                            public void pressedOK() {
                                this.deactivate();
                                PlayerInteractionControlManager.this.getInShipControlManager().popupShipRebootDialog(var1);
                            }
                        }).getInputPanel().onInit();
                        var14.getInputPanel().setOkButtonText(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_48);
                        var14.getInputPanel().setCancelButtonText(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_53);
                        var14.activate();
                    }

                    return var3;
                } else {
                    if (this.buildToolsManager.getBuildToolCreateDocking().potentialCreateDockPos != null) {
                        if (this.buildToolsManager.getBuildToolCreateDocking().docker == null) {
                            VoidUniqueSegmentPiece var12;
                            ElementInformation var7 = ElementKeyMap.getInfoFast((var12 = this.buildToolsManager.getBuildToolCreateDocking().potentialCreateDockPos).getType());
                            this.buildToolsManager.getBuildToolCreateDocking().rail = new VoidUniqueSegmentPiece(var12);
                            Oriencube var9;
                            Oriencube var11 = (var9 = (Oriencube) BlockShapeAlgorithm.getAlgo(var7.getBlockStyle(), var12.getOrientation())).getMirrorAlgo();
                            boolean var6 = false;

                            byte var5;
                            for(var5 = 0; var5 < 24; ++var5) {
                                if (BlockShapeAlgorithm.algorithms[var7.getBlockStyle().id - 1][var5].getClass() == var11.getClass()) {
                                    var6 = true;
                                    break;
                                }
                            }

                            assert var6;

                            var12.setOrientation(var5);
                            var12.voidPos.add(Element.DIRECTIONSi[Element.switchLeftRight(var9.getOrientCubePrimaryOrientation())]);
                            var12.setType((short)663);
                            SegmentCollisionCheckerCallback var8 = new SegmentCollisionCheckerCallback();
                            if (var12.getSegmentController().getCollisionChecker().checkPieceCollision(var12, var8, true)) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_4, 0.0F);
                                return 0;
                            }

                            this.buildToolsManager.getBuildToolCreateDocking().docker = var12;
                            this.buildToolsManager.getBuildToolCreateDocking().potentialCreateDockPos = null;
                        }
                    } else if (this.buildToolsManager.getBuildToolCreateDocking().docker != null && this.buildToolsManager.getBuildToolCreateDocking().core == null) {
                        if (this.buildToolsManager.getBuildToolCreateDocking().potentialCore != null) {
                            SegmentCollisionCheckerCallback var13 = new SegmentCollisionCheckerCallback();
                            if (this.buildToolsManager.getBuildToolCreateDocking().potentialCore.getSegmentController().getCollisionChecker().checkPieceCollision(this.buildToolsManager.getBuildToolCreateDocking().potentialCore, var13, true)) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_5, 0.0F);
                                return 0;
                            }

                            this.buildToolsManager.getBuildToolCreateDocking().core = this.buildToolsManager.getBuildToolCreateDocking().potentialCore;
                            this.buildToolsManager.getBuildToolCreateDocking().potentialCore = null;
                        } else {
                            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_6, 0.0F);
                        }
                    } else if (this.buildToolsManager.getBuildToolCreateDocking().docker != null && this.buildToolsManager.getBuildToolCreateDocking().core != null && this.buildToolsManager.getBuildToolCreateDocking().coreOrientation < 0) {
                        this.buildToolsManager.getBuildToolCreateDocking().coreOrientation = this.buildToolsManager.getBuildToolCreateDocking().potentialCoreOrientation;
                    } else {
                        this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_7, 0.0F);
                    }

                    return 0;
                }
            }
        } else {
            System.err.println("[TUTORIAL] cant place because not selected cannon computer: " + this.getInShipControlManager().getShipControlManager().getSegmentBuildController().getSelectedBlock());
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_1, 0.0F);
            return 0;
        }
    }

    public int buildBlock(EditableSendableSegmentController var1, Vector3f var2, Vector3f var3, BuildCallback var4, DimensionFilter var5, SymmetryPlanes var6, float var7) {
        short var8 = this.getSelectedType();
        this.stacked = false;
        for(SymmetryPlane symmetryPlane : BetterBuilding.getInstance().getAllPlanes()) {
            BetterBuilding.getInstance().currentPlane = symmetryPlane;
            if(checkCanBuild(var1, null, var8) != 0) {
                if (!this.stacked && this.buildToolsManager.slabSize.setting > 0 && ElementKeyMap.isValidType(var8) && ElementKeyMap.getInfoFast(var8).slab == 0 && ElementKeyMap.getInfoFast(var8).slabIds != null && ElementKeyMap.getInfoFast(var8).slabIds.length == 3) {
                    var8 = ElementKeyMap.getInfoFast(var8).slabIds[this.buildToolsManager.slabSize.setting - 1];
                }

                Vector3i var10 = new Vector3i(1, 1, 1);
                if (isAdvancedBuildMode(this.getState())) {
                    var10.set(this.getBuildToolsManager().getSize());
                }

                if (this.getBuildToolsManager().isCopyMode()) {
                    symmetryPlane.setMode(SymmetryMode.COPY);
                } else if (this.getBuildToolsManager().isPasteMode()) {
                    symmetryPlane.setMode(SymmetryMode.PASTE);
                } else if (this.getBuildToolsManager().getBuildHelper() != null && !this.getBuildToolsManager().getBuildHelper().placed) {
                    symmetryPlane.setMode(SymmetryMode.PLACE);
                }

                SegmentPiece var26;
                if (var8 != 0 && !symmetryPlane.inPlaceMode()) {
                    if (this.getBuildToolsManager().isSelectMode()) {
                        return 0;
                    } else if (this.getBuildToolsManager().selectionPlaced) {
                        this.getBuildToolsManager().selectionPlaced = false;
                        return 0;
                    } else if (this.getState().getPlayer().getInventory((Vector3i)null).isSlotEmpty(this.selectedSlot)) {
                        if (!symmetryPlane.inPlaceMode()) {
                            this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_12, 0.0F);
                        }

                        return 0;
                    } else {
                        assert this.getState().getPlayer().getInventory((Vector3i)null).getCount(this.selectedSlot, var8) > 0 : ElementKeyMap.toString(var8);

                        int var20 = this.getState().getPlayer().getInventory((Vector3i)null).getCount(this.selectedSlot, var8);

                        String var23;
                        try {
                            ElementInformation var22 = ElementKeyMap.getInfo(var8);
                            PlayerContextHelpDialog var29;
                            if (var1.isUsingOldPower() && (var22.isReactorChamberAny() || var22.getId() == 1008 || var22.getId() == 1010 || var22.getId() == 1009)) {
                                var23 = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_77, new Object[]{var1.getElementClassCountMap().get((short)2) + var1.getElementClassCountMap().get((short)331)});
                                if (var1.isDocked()) {
                                    var23 = Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_80;
                                }

                                (var29 = new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACED_NEW_REACTOR_ON_OLD_SHIP, var23, true)).ignoreToggle = false;
                                var29.activate();
                                return 0;
                            }

                            if (var1.hasAnyReactors() && (var22.getId() == 2 || var22.getId() == 331)) {
                                (var29 = new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACED_OLD_POWER_ON_NEW_SHIP, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_79, true)).ignoreToggle = false;
                                var29.activate();
                                return 0;
                            }

                            if ((var22.isReactorChamberAny() || var22.getId() == 1008 || var22.getId() == 1010) && var1 instanceof ManagedSegmentController && ((ManagedSegmentController)var1).getManagerContainer().getPowerInterface().isAnyRebooting()) {
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_68);
                                return 0;
                            }

                            if (EngineSettings.CONTEXT_HELP_STABILIZER_EFFICIENCY_PLACE.isOn() && var8 == 1009 && BuildModeDrawer.currentStabEfficiency < 1.0D) {
                                var23 = StringTools.formatPointZero(BuildModeDrawer.currentStabEfficiency * 100.0D);
                                (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_STABILIZER_EFFICIENCY_PLACE, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_66, new Object[]{var23}), true)).activate();
                                return 0;
                            }

                            if (EngineSettings.CONTEXT_HELP_PLACE_MODULE_WITHOUT_COMPUTER_WARNING.isOn()) {
                                var26 = this.getSelectedBlockByActiveController();
                                if (var22.needsComputer()) {
                                    if (var26 == null || var22.getComputer() != var26.getType()) {
                                        (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACE_MODULE_WITHOUT_COMPUTER_WARNING, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_62, new Object[]{ElementKeyMap.getInfoFast(var22.getComputer()).getName(), KeyboardMappings.SELECT_MODULE.getKeyChar()}), true)).activate();
                                        return 0;
                                    }
                                } else if (var22.getId() == 689 && (var26 == null || !ElementKeyMap.isValidType(var26.getType()) || !ElementKeyMap.getInfoFast(var26.getType()).isInventory())) {
                                    (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACE_MODULE_WITHOUT_COMPUTER_WARNING, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_63, new Object[]{ElementKeyMap.getInfoFast((short)120).getName(), KeyboardMappings.SELECT_MODULE.getKeyChar()}), true)).activate();
                                    return 0;
                                }
                            }

                            if (EngineSettings.CONTEXT_HELP_PLACE_REACTOR_WITH_LOW_STABILIZATION.isOn() && var22.id == 1008 && var1 instanceof ManagedSegmentController && ((ManagedSegmentController)var1).getManagerContainer().getPowerInterface().getStabilizerEfficiencyTotal() < 0.5D) {
                                (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACE_REACTOR_WITH_LOW_STABILIZATION, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_69, true)).activate();
                                return 0;
                            }

                            final short finalVar = var8;
                            if (EngineSettings.CONTEXT_HELP_PLACE_CHAMBER_WITHOUT_CONDUIT_WARNING.isOn() && var22.isReactorChamberGeneral() && !this.surroundCondition(var1, var2, var3, var7, var10, new PlayerInteractionControlManager.SurroundBlockCondition() {
                                public boolean ok(ElementInformation var1) {
                                    System.err.println(var1 + " INFO SPEC " + var1.isReactorChamberSpecific() + "; placing " + ElementKeyMap.toString(finalVar) + "; block root: " + var1.chamberRoot + "; " + var1.isReactorChamberGeneral() + "; " + finalVar + "; " + var1.id);
                                    return var1.id == 1010 || var1.isReactorChamberGeneral() && finalVar == var1.getId() || var1.isReactorChamberSpecific() && finalVar == var1.chamberRoot;
                                }
                            })) {
                                (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACE_CHAMBER_WITHOUT_CONDUIT_WARNING, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_70, true)).activate();
                                return 0;
                            }

                            if (EngineSettings.CONTEXT_HELP_PLACE_CONDIUT_WITHOUT_CHAMBER_OR_MAIN_WARNING.isOn() && var22.id == 1010 && !this.surroundCondition(var1, var2, var3, var7, var10, new PlayerInteractionControlManager.SurroundBlockCondition() {
                                public boolean ok(ElementInformation var1) {
                                    return var1.id == 1010 || var1.id == 1008 || var1.isReactorChamberAny();
                                }
                            })) {
                                (new PlayerContextHelpDialog(this.getState(), EngineSettings.CONTEXT_HELP_PLACE_CONDIUT_WITHOUT_CHAMBER_OR_MAIN_WARNING, StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_71, new Object[]{KeyboardMappings.ACTIVATE.getKeyChar()}), true)).activate();
                                return 0;
                            }

                            if (var22.getBlockStyle() != BlockStyle.NORMAL) {
                                System.err.println("[CLIENT] BLOCK style: " + var22.getBlockStyle() + "; ORIENTATION: " + this.getBlockOrientation() + "; " + BlockShapeAlgorithm.algorithms[var22.getBlockStyle().id - 1][this.getBlockOrientation()].toString());
                            }

                            BlockOrientation var28 = ElementInformation.convertOrientation(var22, (byte)this.getBlockOrientation());
                            BuildHelper var25 = null;
                            if (this.getBuildToolsManager().getBuildHelper() != null && this.getBuildToolsManager().buildHelperReplace) {
                                var25 = this.getBuildToolsManager().getBuildHelper();
                            }

                            BuildInstruction var24 = new BuildInstruction(var1);
                            int var19;
                            if ((var19 = var1.getNearestIntersection(var8, var2, var3, var4, var28.blockOrientation, var28.activateBlock, var5, var10, var20, var7, symmetryPlane, var25, var24)) > 0) {
                                this.undo.add(0, var24);

                                while(this.undo.size() > (Integer)EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                                    this.undo.remove(this.undo.size() - 1);
                                }

                                this.redo.clear();
                                this.getState().getController().queueUIAudio("0022_action - buttons push big");
                                if (var8 == 56) {
                                    this.getState().getController().popupInfoTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_13, new Object[]{KeyboardMappings.ACTIVATE.getKeyChar()}), 0.0F);
                                }

                                return var19;
                            }
                        } catch (ElementPositionBlockedException var18) {
                            var23 = Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_14;
                            if (var18.userData != null) {
                                if (var18.userData instanceof SegmentController) {
                                    var23 = ((SegmentController)var18.userData).getRealName();
                                } else if (var18.userData instanceof SimpleTransformableSendableObject) {
                                    var23 = ((SimpleTransformableSendableObject)var18.userData).toNiceString();
                                } else if (var18.userData instanceof String) {
                                    var23 = (String)var18.userData;
                                }
                            }

                            this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_15, new Object[]{var23}), 0.0F);
                        } catch (BlockedByDockedElementException var19) {
                            this.getState().getWorldDrawer().getBuildModeDrawer().addBlockedDockIndicator(var19.to, (SegmentController)null, (DockingBlockCollectionManager)null);
                            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_16, 0.0F);
                        } catch (BlockNotBuildTooFast var21) {
                            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_17, 0.0F);
                        }

                        return 0;
                    }
                } else {
                    if (!symmetryPlane.inPlaceMode()) {
                        this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_11, 0.0F);
                    } else {
                        Vector3f var11;
                        try {
                            boolean var21 = symmetryPlane.inPlaceMode();
                            BuildInstruction var12 = new BuildInstruction(var1);
                            if (this.getBuildToolsManager().isCopyMode() || this.getBuildToolsManager().getBuildHelper() != null && !this.getBuildToolsManager().getBuildHelper().placed) {
                                var26 = var1.getNearestPiece(var2, var3, var7, var10, var10);
                            } else {
                                var26 = var1.getNextToNearestPiece(var2, var3, new Vector3i(), var7, new Vector3i(), new Vector3i());
                            }

                            if (var21 && var26 != null) {
                                Vector3i var13 = var26.getAbsolutePos(new Vector3i());
                                SymmetryMode symmetryMode = symmetryPlane.getMode();
                                switch(symmetryMode) {
                                    case XY:
                                        symmetryPlane.getPlane().z = var13.z;
                                        symmetryPlane.setEnabled(true);
                                        break;
                                    case XZ:
                                        symmetryPlane.getPlane().y = var13.y;
                                        symmetryPlane.setEnabled(true);
                                        break;
                                    case YZ:
                                        symmetryPlane.getPlane().x = var13.x;
                                        symmetryPlane.setEnabled(true);
                                        break;
                                    case COPY:
                                        if (this.buildToolsManager.isSelectMode()) {
                                            System.out.println("COPY Case mode_copy");
                                        } else {
                                            if (this.getBuildToolsManager().selectionPlaced) {
                                                System.out.println("COPY Case mode_copy no sel");
                                                this.getBuildToolsManager().selectionPlaced = false;
                                                return 0;
                                            }

                                            System.err.println("[CLIENT] COPY AREA SET " + var13);
                                            this.getBuildToolsManager().setCopyArea(var1, var13, var10);
                                            this.getBuildToolsManager().setCopyMode(false);
                                            symmetryPlane.setPlaceMode(false);
                                            this.getPlayerCharacterManager().setPlaceMode(false);
                                        }

                                        return 0;
                                    case PASTE:
                                        if (this.getBuildToolsManager().canPaste()) {
                                            this.getBuildToolsManager().getCopyArea().build(var1, var13, var12, symmetryPlane);
                                            this.undo.add(0, var12);

                                            while(this.undo.size() > (Integer)EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                                                this.undo.remove(this.undo.size() - 1);
                                            }
                                        }
                                    case PLACE:
                                        if (this.getBuildToolsManager().getBuildHelper() != null) {
                                            this.getBuildToolsManager().getBuildHelper().placed = true;
                                            var11 = new Vector3f((float)(var13.x - 16), (float)(var13.y - 16), (float)(var13.z - 16));
                                            this.getBuildToolsManager().getBuildHelper().localTransform.origin.set(var11);
                                            this.getBuildToolsManager().getBuildHelper().placedPos = new Vector3i(var13);
                                        }
                                }

                                symmetryPlane.setPlaceMode(false);
                                this.getBuildToolsManager().setCopyMode(false);
                            } else {
                                System.err.println("[CLIENT] NO NEAREST PIECE TO BUILD ON");
                            }
                        } catch (ElementPositionBlockedException var21) {
                            var11 = null;
                            var21.printStackTrace();
                        } catch (BlockNotBuildTooFast var22) {
                            var11 = null;
                            var22.printStackTrace();
                        }
                    }


                    return 0;
                }
            }
        }
        return 0;
    }

    private boolean surroundCondition(EditableSendableSegmentController var1, Vector3f var2, Vector3f var3, float var4, Vector3i var5, PlayerInteractionControlManager.SurroundBlockCondition var6) throws ElementPositionBlockedException, BlockNotBuildTooFast {
        SegmentPiece var7;
        if ((var7 = var1.getNextToNearestPiece(new Vector3f(var2), new Vector3f(var3), new Vector3i(), var4, var5, new Vector3i())) != null) {
            for(int var8 = 0; var8 < 6; ++var8) {
                Vector3i var9;
                (var9 = var7.getAbsolutePos(new Vector3i())).add(Element.DIRECTIONSi[var8]);
                SegmentPiece var10;
                if ((var10 = var1.getSegmentBuffer().getPointUnsave(var9)) != null && ElementKeyMap.isValidType(var10.getType()) && var6.ok(ElementKeyMap.getInfo(var10.getType()))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isAdvancedBuildMode(GameClientState var0) {
        PlayerInteractionControlManager var1 = var0.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
        return !BuildConstructionCommand.issued && (var1.stickyAdvBuildMode || KeyboardMappings.BUILD_MODE_FIX_CAM.isDownSI(var0)) && var1.isInAnyBuildMode();
    }

    public boolean isAdvancedBuildMode() {
        return isAdvancedBuildMode(this.getState());
    }

    public boolean isInAnyBuildMode() {
        return this.isInAnyCharacterBuildMode() || this.isInAnyStructureBuildMode();
    }

    public boolean isInAnyCharacterBuildMode() {
        return this.getPlayerCharacterManager().isTreeActive();
    }

    public boolean isInAnyStructureBuildMode() {
        return this.getSegmentControlManager().getSegmentBuildController().isTreeActive() || this.getInShipControlManager().getShipControlManager().getSegmentBuildController().isTreeActive();
    }

    public int getBlockOrientation() {
        return this.blockOrientation;
    }

    public void setBlockOrientation(int var1) {
        this.blockOrientation = var1;
    }

    public BuildToolsManager getBuildToolsManager() {
        return this.buildToolsManager;
    }

    public InShipControlManager getInShipControlManager() {
        return this.inShipControlManager;
    }

    public PlayerExternalController getPlayerCharacterManager() {
        return this.playerCharacterManager;
    }

    public HotbarLayout getHotbarLayout() {
        return this.hotbarLayout;
    }

    public boolean checkActivate(final SegmentPiece var1) {
        if (var1 != null) {
            if (!this.getState().getController().allowedToActivate(var1)) {
                return false;
            }

            if (var1.getSegmentController() instanceof Planet && (var1.getType() == 6 || var1.getType() == 38 || var1.getType() == 414 || var1.getType() == 416 || var1.getType() == 4 || var1.getType() == 39 || var1.getType() == 46 || var1.getType() == 54 || var1.getType() == 334 || var1.getType() == 332 || var1.getType() == 344)) {
                this.getState().getController().popupGameTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_18, 0.0F);
                return false;
            }

            if (var1.getType() == 56) {
                this.getState().getCharacter().activateGravity(var1);
                return true;
            }

            if (ElementInformation.isMedical(var1.getType())) {
                this.getState().getCharacter().activateMedical(var1);
                return true;
            }

            if (var1.getType() == 542) {
                if (var1.getSegmentController().getConfigManager().apply(StatusEffectType.WARP_FREE_TARGET, false)) {
                    (new PlayerGameTextInput("EDIT_FREE_WARP_TARGET", this.getState(), 80, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_73, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_74, var1.getAbsolutePos(new Vector3i()).toStringPure()) {
                        public String[] getCommandPrefixes() {
                            return null;
                        }

                        public void onDeactivate() {
                            PlayerInteractionControlManager.this.suspend(false);
                        }

                        public String handleAutoComplete(String var1x, TextCallback var2, String var3) throws PrefixNotFoundException {
                            return null;
                        }

                        public boolean onInput(String var1x) {
                            try {
                                Vector3i var3 = Vector3i.parseVector3iFree(var1x);
                                this.getState().getPlayer().sendSimpleCommand(SimplePlayerCommands.SET_FREE_WARP_TARGET, new Object[]{var1.getSegmentController().getId(), var1.getAbsoluteIndex(), var3.x, var3.y, var3.z});
                                return true;
                            } catch (Exception var2) {
                                var2.printStackTrace();
                                this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_75, 0.0F);
                                return false;
                            }
                        }

                        public boolean isOccluded() {
                            return false;
                        }

                        public void onFailedTextCheck(String var1x) {
                        }
                    }).activate();
                } else {
                    this.getState().getController().popupGameTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_76, 0.0F);
                }

                return true;
            }

            if (var1.getType() == 94) {
                System.err.println("[CLIENT][SPAWN] attempting to set spawn point to " + var1);
                this.getState().getController().setSpawnPoint(var1);
                return true;
            }

            if (var1.getType() == 121) {
                this.getState().getController().popupGameTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_19 + var1.getSegment().getSegmentController().toNiceString(), 0.0F);
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().aiConfigurationAction(var1);
                return true;
            }

            if (var1.getType() == 347) {
                this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().shopAction();
                return true;
            }

            if (var1.getType() == 670) {
                long var29 = var1.getAbsoluteIndexWithType4();
                String var27;
                if ((var27 = (String)var1.getSegmentController().getTextMap().get(var29)) == null) {
                    ((ClientSegmentProvider)var1.getSegmentController().getSegmentProvider()).getSendableSegmentProvider().clientTextBlockRequest(var29);
                    var27 = "";
                }

                (new PlayerGameTextInput("EDIT_LOGIC_NAME", this.getState(), 16, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_20, "", var27) {
                    public String[] getCommandPrefixes() {
                        return null;
                    }

                    public void onDeactivate() {
                        PlayerInteractionControlManager.this.suspend(false);
                    }

                    public String handleAutoComplete(String var1x, TextCallback var2, String var3) throws PrefixNotFoundException {
                        return null;
                    }

                    public boolean onInput(String var1x) {
                        SendableSegmentProvider var2 = ((ClientSegmentProvider)var1.getSegment().getSegmentController().getSegmentProvider()).getSendableSegmentProvider();
                        TextBlockPair var3;
                        (var3 = new TextBlockPair()).block = ElementCollection.getIndex4(var1.getAbsoluteIndex(), (short)670);
                        var3.text = var1x;
                        var2.getNetworkObject().textBlockResponsesAndChangeRequests.add(new RemoteTextBlockPair(var3, false));
                        return true;
                    }

                    public boolean isOccluded() {
                        return false;
                    }

                    public void onFailedTextCheck(String var1x) {
                    }
                }).activate();
                return true;
            }

            if (var1.getType() == 291) {
                if (this.getState().getPlayer().getFactionId() != 0) {
                    if (this.getState().getFactionManager().getFaction(this.getState().getPlayer().getFactionId()) != null) {
                        this.activateFactionDiag(var1.getSegmentController());
                    } else {
                        this.getState().getController().popupGameTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_22 + var1.getSegment().getSegmentController().toNiceString(), 0.0F);
                    }
                } else if (!this.activateResetFactionIfOwner(var1.getSegmentController())) {
                    this.getState().getController().popupGameTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_21 + var1.getSegment().getSegmentController().toNiceString(), 0.0F);
                }

                return true;
            }

            SegmentController var2;
            if (var1.getType() == 113) {
                if ((var2 = var1.getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var2).getManagerContainer() instanceof LiftContainerInterface) {
                    LiftContainerInterface var20;
                    LiftCollectionManager var24 = (var20 = (LiftContainerInterface)((ManagedSegmentController)var2).getManagerContainer()).getLiftManager();
                    Vector3i var30 = var1.getAbsolutePos(new Vector3i());
                    var24.activate(var30, true);
                    var20.handleClientRemoteLift(var30);
                }

                return true;
            }

            PowerAddOn var4;
            if (var1.getType() != 2 && var1.getType() != 331) {
                if (var1.getType() == 978) {
                    if ((var2 = var1.getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var2).getManagerContainer() instanceof PowerManagerInterface) {
                        PowerManagerInterface var18;
                        var4 = (var18 = (PowerManagerInterface)((ManagedSegmentController)var2).getManagerContainer()).getPowerAddOn();
                        if (this.lastPowerMsg != null) {
                            this.getState().getController().endPopupMessage(this.lastPowerMsg);
                        }

                        long var28 = var1.getAbsoluteIndex();
                        double var7 = 0.0D;
                        double var9 = 0.0D;
                        Iterator var11 = var18.getPowerBatteryManager().getElementCollections().iterator();

                        while(var11.hasNext()) {
                            PowerBatteryUnit var25;
                            if ((var25 = (PowerBatteryUnit)var11.next()).contains(var28)) {
                                var7 = var25.getMaxPower();
                                var9 = var25.getRecharge();
                                break;
                            }
                        }

                        this.lastPowerMsg = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_49, new Object[]{StringTools.formatPointZero(var4.getBatteryPower()), StringTools.formatPointZero(var4.getBatteryMaxPower()), StringTools.formatPointZero(var4.getRecharge()), StringTools.formatPointZero(var7), StringTools.formatPointZero(var9)});
                        this.getState().getController().popupInfoTextMessage(this.lastPowerMsg, 0.0F);
                        return true;
                    }
                } else if (var1.getType() == 8) {
                    if ((var2 = var1.getSegment().getSegmentController()) instanceof Ship) {
                        this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().activateThrustManager((Ship)var2);
                        return true;
                    }

                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_23, 0.0F);
                } else if (var1.getType() != 3 && var1.getType() != 478) {
                    if (ElementKeyMap.canOpen(var1.getType())) {
                        if (var1.getSegmentController().isVirtualBlueprint()) {
                            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_26, 0.0F);
                        } else if ((var2 = var1.getSegment().getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var2).getManagerContainer() instanceof InventoryHolder) {
                            Vector3i var12 = var1.getAbsolutePos(new Vector3i());
                            ManagerContainer var19;
                            Inventory var26;
                            if ((var26 = (var19 = ((ManagedSegmentController)var2).getManagerContainer()).getInventory(var1.getAbsoluteIndex())) == null) {
                                System.err.println("[CLIENT] WARNING: OPEN BLOCK: Inventory NULL: " + var12 + ": Holder: " + var19 + "; Invs: " + var19.printInventories() + ";");
                            }

                            this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().inventoryAction(var26);
                        }

                        return true;
                    }

                    if (ElementKeyMap.isReactor(var1.getType()) && (var2 = var1.getSegmentController()).hasAnyReactors()) {
                        (new ReactorTreeDialog(this.getState(), (ManagedSegmentController)var2)).activate();
                    }
                } else if ((var2 = var1.getSegment().getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var2).getManagerContainer() instanceof ShieldContainerInterface) {
                    ShieldContainerInterface var3;
                    ShieldAddOn var17 = (var3 = (ShieldContainerInterface)((ManagedSegmentController)var2).getManagerContainer()).getShieldAddOn();
                    if (this.lastShieldMsg != null) {
                        this.getState().getController().endPopupMessage(this.lastShieldMsg);
                    }

                    if (var17.isUsingLocalShields()) {
                        ShieldLocal var5;
                        if ((var5 = var17.getShieldLocalAddOn().getContainingShield(var3, var1.getAbsoluteIndex())) != null) {
                            this.lastShieldMsg = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_72, new Object[]{var5.getPosString(), StringTools.formatPointZero(var5.radius), StringTools.formatPointZero(var5.getShields()), StringTools.formatPointZero(var5.getShieldCapacity()), var5.supportIds.size(), StringTools.formatPointZero(var5.getRechargeRate()), StringTools.formatPointZero(var5.getShieldUpkeep()), StringTools.formatPointZero(var5.getShields() < var5.getShieldCapacity() ? var5.getPowerConsumedPerSecondCharging() : var5.getPowerConsumedPerSecondResting())});
                        }
                    } else {
                        int var23 = var17.getShields() >= var17.getShieldCapacity() ? (int)(var17.getShieldRechargeRate() * (double)VoidElementManager.SHIELD_FULL_POWER_CONSUMPTION) : (int)(var17.getShieldRechargeRate() * (double)VoidElementManager.SHIELD_RECHARGE_POWER_CONSUMPTION);
                        this.lastShieldMsg = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_25, new Object[]{(int)var17.getShields(), (int)var17.getShieldCapacity(), (int)var17.getShieldRechargeRate(), var23});
                    }

                    if (this.lastShieldMsg != null) {
                        this.getState().getController().popupInfoTextMessage(this.lastShieldMsg, 0.0F);
                    }

                    return true;
                }
            } else if ((var2 = var1.getSegmentController()) instanceof ManagedSegmentController && ((ManagedSegmentController)var2).getManagerContainer() instanceof PowerManagerInterface) {
                var4 = ((PowerManagerInterface)((ManagedSegmentController)var2).getManagerContainer()).getPowerAddOn();
                if (this.lastPowerMsg != null) {
                    this.getState().getController().endPopupMessage(this.lastPowerMsg);
                }

                this.lastPowerMsg = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_24, new Object[]{(int)var4.getPowerSimple(), (int)var4.getMaxPower(), (int)var4.getRecharge()});
                this.getState().getController().popupInfoTextMessage(this.lastPowerMsg, 0.0F);
                return true;
            }

            if (var1.getType() == 683) {
                boolean var21 = true;
                Iterator var16 = this.getState().getController().getPlayerInputs().iterator();

                while(var16.hasNext()) {
                    if ((DialogInterface)var16.next() instanceof PlayerRaceInput) {
                        var21 = false;
                    }
                }

                if (var21) {
                    (new PlayerRaceInput(this.getState(), var1)).activate();
                }

                return true;
            }

            if (var1.getType() == 687) {
                TransporterCollectionManager var14;
                if (var1.getSegmentController() instanceof ManagedSegmentController && ((ManagedSegmentController)var1.getSegmentController()).getManagerContainer() instanceof TransporterModuleInterface && (var14 = (TransporterCollectionManager)((TransporterModuleInterface)((ManagedSegmentController)var1.getSegmentController()).getManagerContainer()).getTransporter().getCollectionManagersMap().get(var1.getAbsoluteIndex())) != null) {
                    (new PlayerTransporterInput(this.getState(), var14)).activate();
                }

                return true;
            }

            if (var1.getType() == 479) {
                String var15;
                if ((var15 = (String)var1.getSegment().getSegmentController().getTextMap().get(ElementCollection.getIndex4(var1.getAbsoluteIndex(), (short)var1.getOrientation()))) == null) {
                    var15 = "";
                }

                final PlayerTextAreaInput var13;
                (var13 = new PlayerTextAreaInput("EDIT_DISPLAY_BLOCK_POPUP", this.getState(), 400, 300, 240, 11, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_28, "", var15, FontSize.SMALL) {
                    public void onDeactivate() {
                        PlayerInteractionControlManager.this.suspend(false);
                    }

                    public String[] getCommandPrefixes() {
                        return null;
                    }

                    public boolean onInput(String var1x) {
                        SendableSegmentProvider var2 = ((ClientSegmentProvider)var1.getSegment().getSegmentController().getSegmentProvider()).getSendableSegmentProvider();
                        TextBlockPair var3;
                        (var3 = new TextBlockPair()).block = ElementCollection.getIndex4(var1.getAbsoluteIndex(), (short)var1.getOrientation());
                        var3.text = var1x;
                        System.err.println("[CLIENT]Text entry:\n\"" + var3.text + "\"");
                        var2.getNetworkObject().textBlockResponsesAndChangeRequests.add(new RemoteTextBlockPair(var3, false));
                        return true;
                    }

                    public String handleAutoComplete(String var1x, TextCallback var2, String var3) throws PrefixNotFoundException {
                        return null;
                    }

                    public boolean isOccluded() {
                        return false;
                    }

                    public void onFailedTextCheck(String var1x) {
                    }
                }).getTextInput().setAllowEmptyEntry(true);
                GUITextButton var22;
                (var22 = new GUITextButton(this.getState(), 100, 20, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_29, new GUICallback() {
                    public void callback(GUIElement var1x, MouseEvent var2) {
                        if (var2.pressedLeftMouse()) {
                            String var3 = StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_30, new Object[]{Replacements.getVariables(var1.getSegmentController().isUsingOldPower())});
                            PlayerGameOkCancelInput var4;
                            (var4 = new PlayerGameOkCancelInput("DISPLAY_BLOCK_HELP_D_", PlayerInteractionControlManager.this.getState(), 600, 500, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_31, var3, FontStyle.def) {
                                public boolean isOccluded() {
                                    return false;
                                }

                                public void onDeactivate() {
                                    this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().hinderInteraction(400);
                                }

                                public void pressedOK() {
                                    this.getState().getController().queueUIAudio("0022_action - buttons push medium");
                                    this.deactivate();
                                }
                            }).getInputPanel().setCancelButton(false);
                            var4.activate();
                        }

                    }

                    public boolean isOccluded() {
                        return false;
                    }
                }) {
                    public void draw() {
                        this.setPos(var13.getInputPanel().getButtonCancel().getPos().x + var13.getInputPanel().getButtonCancel().getWidth() + 10.0F, var13.getInputPanel().getButtonCancel().getPos().y, 0.0F);
                        super.draw();
                    }
                }).setPos(300.0F, -40.0F, 0.0F);
                var13.getInputPanel().onInit();
                ((GUIDialogWindow)var13.getInputPanel().background).getMainContentPane().getContent(0).attach(var22);
                var13.activate();
                return true;
            }

            this.checkMakeCustomOutput(var1);
        }

        return false;
    }

    public void activateFactionDiag(SegmentController var1) {
        (new FactionBlockDialog(this.getState(), var1, this)).activate();
    }

    private void checkMakeCustomOutput(SegmentPiece p) {
        //INSERTED CODE @972
        SegmentPieceActivateByPlayer event = new SegmentPieceActivateByPlayer(p, this.getPlayerCharacterManager().getState().getPlayer(), this);
        StarLoader.fireEvent(event, false);
        ///
        if (!this.getPlayerCharacterManager().canEnter(p.getType())) {
            System.err.println("[CLIENT] ACTIVATE BLOCK (std) " + p);
            PositionControl var2;
            if ((var2 = p.getSegment().getSegmentController().getControlElementMap().getControlledElements((short)56, p.getAbsolutePos(new Vector3i()))).getControlMap().size() > 0) {
                long var3 = var2.getControlPosMap().iterator().nextLong();
                SegmentPiece var5;
                if ((var5 = p.getSegment().getSegmentController().getSegmentBuffer().getPointUnsave(var3)) != null) {
                    this.getState().getCharacter().activateGravity(var5);
                }
            }

            if (ElementKeyMap.getInfo(p.getType()).canActivate()) {
                boolean var6 = !p.isActive();
                if (p.getType() == 667 && p.isActive()) {
                    var6 = true;
                }

                long var4 = ElementCollection.getEncodeActivation(p, true, var6, false);
                p.getSegment().getSegmentController().sendBlockActivation(var4);
            }

        } else {
            if (this.getPlayerCharacterManager().isActive()) {
                if (!this.getPlayerCharacterManager().checkEnterAndEnterIfPossible(p)) {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_32, 0.0F);
                    return;
                }
            } else if (this.getInShipControlManager().isActive()) {
                this.getInShipControlManager().exitShip(false);
            }

        }
    }

    public SegmentControlManager getSegmentControlManager() {
        return this.segmentControlManager;
    }

    public SimpleTransformableSendableObject getSelectedEntity() {
        return this.selectedEntity;
    }

    public SimpleTransformableSendableObject getSelectedAITarget() {
        return this.selectedAITarget;
    }

    public void setSelectedAITarget(SimpleTransformableSendableObject var1) {
        this.selectedAITarget = var1;
        this.hasNotMadePin = true;
        if (var1 == null) {
            this.hasNotMadePin = false;
        }

    }

    public void setSelectedEntity(SimpleTransformableSendableObject var1) {
        SimpleTransformableSendableObject var2 = this.selectedEntity;
        this.selectedEntity = var1;
        if (this.selectedEntity != null && this.selectedEntity.getUniqueIdentifier() != null && EngineSettings.P_PHYSICS_DEBUG_ACTIVE.isOn()) {
            GameClientState.debugSelectedObject = this.selectedEntity.getId();
        } else {
            GameClientState.debugSelectedObject = -1;
        }

        if (this.selectedEntity != null && (this.selectedAITarget == null || !this.selectedAITarget.isInClientRange()) && !this.hasNotMadePin) {
            this.selectedAITarget = this.selectedEntity;
        }

        if (var2 != this.selectedEntity) {
            this.getState().getController().onSelectedEntityChanged(var2, this.selectedEntity);
        }

    }

    public int getSelectedSlot() {
        return this.selectedSlot;
    }

    private short getSelectedType() {
        return this.getState().getPlayer().getInventory().getType(this.selectedSlot);
    }

    public int getSelectedSubSlot() {
        return this.selectedSubSlot;
    }

    public int getSlotKey(int var1) {
        if (this.slotKeyMap.containsKey(var1)) {
            return (Integer)this.slotKeyMap.get(var1);
        } else {
            System.err.println("[WARNING] UNKNOWN SLOT KEY: " + var1 + ": " + this.slotKeyMap);
            return -1;
        }
    }

    public void handleKeyEvent(KeyEventInterface var1) {
        if (!PlayerInput.isDelayedFromMainMenuDeactivation()) {
            int var2;
            if ((var2 = this.getState().getController().getPlayerInputs().size()) > 0) {
                ((DialogInterface)this.getState().getController().getPlayerInputs().get(var2 - 1)).handleKeyEvent(var1);
            } else {
                if (KeyboardMappings.getEventKeyRaw(var1) == 1) {
                    this.stickyAdvBuildMode = false;
                }

                label100: {
                    if (this.isInAnyStructureBuildMode()) {
                        if (!KeyboardMappings.getEventKeyState(var1, this.getState()) || !KeyboardMappings.BUILD_MODE_FIX_CAM.isEventKey(var1, this.getState())) {
                            break label100;
                        }

                        if (!this.stickyAdvBuildMode) {
                            if (System.currentTimeMillis() - this.lastPressedAdvBuildModeButton > (long)(Integer)EngineSettings.ADVANCED_BUILD_MODE_STICKY_DELAY.getCurrentState()) {
                                this.lastPressedAdvBuildModeButton = System.currentTimeMillis();
                            } else {
                                System.err.println("[CLIENT] ACTIVATED STICKY MODE");
                                this.stickyAdvBuildMode = true;
                            }
                            break label100;
                        }

                        System.err.println("[CLIENT] DEACTIVATED STICKY MODE");
                    }

                    this.stickyAdvBuildMode = false;
                }

                boolean var4 = KeyboardMappings.CREW_CONTROL.isDown(this.getState());
                if (KeyboardMappings.getEventKeyState(var1, this.getState())) {
                    if (this.getState().getController().getPlayerInputs().isEmpty()) {
                        if ((KeyboardMappings.COPY_AREA_NEXT.isEventKey(var1, this.getState()) || KeyboardMappings.COPY_AREA_PRIOR.isEventKey(var1, this.getState())) && this.getBuildToolsManager().getCopyArea() != null && this.getBuildToolsManager().isPasteMode()) {
                            System.err.println("[CLIENT] Rotating copy area");
                            new Matrix3f();
                            if (KeyboardMappings.COPY_AREA_NEXT.isEventKey(var1, this.getState())) {
                                if (KeyboardMappings.COPY_AREA_X_AXIS.isDown(this.getState())) {
                                    this.getBuildToolsManager().getCopyArea().rotate(1, 0, 0);
                                } else if (KeyboardMappings.COPY_AREA_Z_AXIS.isDown(this.getState())) {
                                    this.getBuildToolsManager().getCopyArea().rotate(0, 0, 1);
                                } else {
                                    this.getBuildToolsManager().getCopyArea().rotate(0, 1, 0);
                                }
                            } else if (KeyboardMappings.COPY_AREA_X_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(-1, 0, 0);
                            } else if (KeyboardMappings.COPY_AREA_Z_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(0, 0, -1);
                            } else {
                                this.getBuildToolsManager().getCopyArea().rotate(0, -1, 0);
                            }
                        }

                        if (var4 && !this.inShipControlManager.getShipControlManager().getSegmentBuildController().isTreeActive() && !this.segmentControlManager.getSegmentBuildController().isTreeActive()) {
                            this.checkAI(var1);
                        }

                        InventorySlot var5;
                        if (KeyboardMappings.DROP_ITEM.isEventKey(var1, this.getState()) && (var5 = this.getState().getPlayer().getInventory().getSlot(this.getSelectedSlot())) != null) {
                            DragDrop var3;
                            (var3 = new DragDrop()).slot = this.getSelectedSlot();
                            var3.count = var5.count();
                            var3.type = var5.getType();
                            var3.subType = 0;
                            var3.invId = this.getState().getPlayer().getId();
                            var3.parameter = -9223372036854775808L;
                            this.getState().getPlayer().getNetworkObject().dropOrPickupSlots.add(new RemoteDragDrop(var3, this.getState().getPlayer().getNetworkObject()));
                        }

                        if (KeyboardMappings.SELECT_ENTITY_NEXT.isEventKey(var1, this.getState())) {
                            this.selectEntity(1);
                        } else if (KeyboardMappings.SELECT_ENTITY_PREV.isEventKey(var1, this.getState())) {
                            this.selectEntity(-1);
                        } else if (KeyboardMappings.SELECT_NEAREST_ENTITY.isEventKey(var1, this.getState())) {
                            this.selectNearestEntity();
                        } else if (KeyboardMappings.SELECT_LOOK_ENTITY.isEventKey(var1, this.getState())) {
                            this.selectLookingAt();
                        } else if (KeyboardMappings.PIN_AI_TARGET.isEventKey(var1, this.getState())) {
                            System.err.println("[CLIENT] SET PIN OF AI TARGET TO " + this.getSelectedEntity());
                            this.setSelectedAITarget(this.getSelectedEntity());
                        }
                    }

                    this.addNetworkInputKeyEvent(var1);
                }

                if (this.getState().getController().getPlayerInputs().isEmpty()) {
                    super.handleKeyEvent(var1);
                }

            }
        }
    }

    public void handleMouseEvent(MouseEvent var1) {
        if (!this.getState().getWorldDrawer().getGuiDrawer().isMouseOnPanel() && !PlayerInput.isDelayedFromMainMenuDeactivation() && this.getState().getController().getPlayerInputs().size() <= 0) {
            if (System.currentTimeMillis() - this.getState().getController().getInputController().getLastDeactivatedMenu() >= 200L) {
                if (var1.pressedMiddleMouse()) {
                    if (isAdvancedBuildMode(this.getState())) {
                        this.switchHotbarWithLookAt();
                    } else {
                        this.switchToLookAt();
                    }
                } else {
                    boolean var2 = isAdvancedBuildMode(this.getState());
                    boolean var3 = this.getBuildToolsManager().getCopyArea() != null && this.getBuildToolsManager().isPasteMode();
                    if (!this.getBuildToolsManager().isInCreateDockingMode() && !var2 && !var3 && EngineSettings.S_ZOOM_MOUSEWHEEL.getCurrentState().equals("SLOTS") && !Keyboard.isKeyDown(KeyboardMappings.SCROLL_MOUSE_ZOOM.getMapping()) && !KeyboardMappings.PLAYER_LIST.isDown(this.getState())) {
                        if (var1.dWheel != 0 && this.getForcedSelect() != 0) {
                            this.forcedSelect = 0;
                        }

                        InventorySlot var4;
                        if ((var4 = this.getState().getPlayer().getInventory().getSlot(this.selectedSlot)) != null && var4.isMultiSlot() && this.selectedSubSlot + (var1.dWheel > 0 ? 1 : 0) < var4.getSubSlots().size() && this.selectedSubSlot + (var1.dWheel < 0 ? -1 : 0) >= 0) {
                            this.selectedSubSlot = FastMath.cyclicModulo(this.selectedSubSlot + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), var4.getSubSlots().size());
                        } else {
                            short[] var5;
                            if (var4 != null && var4.getType() > 0 && ElementKeyMap.getInfoFast(var4.getType()).blocktypeIds != null && this.selectedSubSlot + (var1.dWheel > 0 ? 1 : 0) < this.getState().getBlockSyleSubSlotController().getSelectedStack(var4.getType()).length && this.selectedSubSlot + (var1.dWheel < 0 ? -1 : 0) >= 0) {
                                var5 = this.getState().getBlockSyleSubSlotController().getSelectedStack(var4.getType());
                                this.selectedSubSlot = FastMath.cyclicModulo(this.selectedSubSlot + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), var5.length);
                            } else {
                                if (EngineSettings.S_INVERT_MOUSEWHEEL_HOTBAR.isOn()) {
                                    this.selectedSlot = FastMath.cyclicModulo(this.selectedSlot + (var1.dWheel > 0 ? -1 : (var1.dWheel < 0 ? 1 : 0)), 10);
                                } else {
                                    this.selectedSlot = FastMath.cyclicModulo(this.selectedSlot + (var1.dWheel > 0 ? 1 : (var1.dWheel < 0 ? -1 : 0)), 10);
                                }

                                var4 = this.getState().getPlayer().getInventory().getSlot(this.selectedSlot);
                                this.selectedSubSlot = 0;
                                if (var4 != null && var4.isMultiSlot()) {
                                    if (var1.dWheel < 0) {
                                        this.selectedSubSlot = var4.getSubSlots().size() - 1;
                                    }
                                } else if (var4 != null && var4.getType() > 0 && ElementKeyMap.getInfoFast(var4.getType()).blocktypeIds != null && var1.dWheel < 0) {
                                    var5 = this.getState().getBlockSyleSubSlotController().getSelectedStack(var4.getType());
                                    this.selectedSubSlot = var5.length - 1;
                                }
                            }
                        }

                        this.getState().getPlayer().setSelectedBuildSlot(this.selectedSlot);
                        this.checkOrienationForNewSelectedSlot();
                    }

                    if (this.buildToolsManager.isSelectMode()) {
                        this.buildToolsManager.getSelectMode().handleMouseEvent(this, var1);
                    }

                    if (var3) {
                        new Matrix3f();
                        if (var1.dWheel > 0) {
                            System.err.println("[CLIENT] Rotating copy area +");
                            if (KeyboardMappings.COPY_AREA_X_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(1, 0, 0);
                            } else if (KeyboardMappings.COPY_AREA_Z_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(0, 0, 1);
                            } else {
                                this.getBuildToolsManager().getCopyArea().rotate(0, 1, 0);
                            }
                        } else if (var1.dWheel < 0) {
                            System.err.println("[CLIENT] Rotating copy area -");
                            if (KeyboardMappings.COPY_AREA_X_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(-1, 0, 0);
                            } else if (KeyboardMappings.COPY_AREA_Z_AXIS.isDown(this.getState())) {
                                this.getBuildToolsManager().getCopyArea().rotate(0, 0, -1);
                            } else {
                                this.getBuildToolsManager().getCopyArea().rotate(0, -1, 0);
                            }
                        }
                    }

                    super.handleMouseEvent(var1);
                }
            }
        }
    }

    public void setSelectedSlotForced(int var1, short var2) {
        this.selectedSlot = var1;
        this.selectedSubSlot = 0;
        this.getState().getPlayer().setSelectedBuildSlot(this.selectedSlot);
        InventorySlot var4 = this.getState().getPlayer().getInventory().getSlot(this.selectedSlot);
        if (var2 != 0 && var4 != null && var4.getType() == -32768) {
            List var5 = var4.getSubSlots();

            for(int var3 = 0; var3 < var5.size(); ++var3) {
                if (((InventorySlot)var5.get(var3)).getType() == var2) {
                    this.selectedSubSlot = var3;
                }
            }
        }

    }

    private void switchHotbarWithLookAt() {
        Vector3f var1 = new Vector3f(Controller.getCamera().getPos());
        if (this.isInAnyCharacterBuildMode() && this.getState().getCharacter() != null) {
            var1.set(this.getState().getCharacter().getHeadWorldTransform().origin);
        }

        Vector3f var2 = new Vector3f(var1);
        Vector3f var3;
        if (!Float.isNaN((var3 = new Vector3f(Controller.getCamera().getForward())).x)) {
            if (isAdvancedBuildMode(this.getState())) {
                Vector3f var4 = new Vector3f(this.getState().getWorldDrawer().getAbsoluteMousePosition());
                var3.sub(var4, var1);
                var3.normalize();
            }

            var3.scale(100.0F);
            var2.add(var3);
            CubeRayCastResult var6;
            ClosestRayResultCallback var11;
            if ((var11 = ((PhysicsExt)this.getState().getPhysics()).testRayCollisionPoint(var1, var2, false, (SimpleTransformableSendableObject)null, (SegmentController)null, false, true, false)).hasHit() && var11 instanceof CubeRayCastResult && (var6 = (CubeRayCastResult)var11).getSegment() != null) {
                SegmentPiece var7;
                short var9 = (var7 = new SegmentPiece(var6.getSegment(), var6.getCubePos())).getType();
                System.err.println("[CLIENT] looking at block type: " + var7.toString());
                Inventory var8;
                Iterator var10 = (var8 = this.getState().getPlayer().getInventory()).getSlots().iterator();

                while(var10.hasNext()) {
                    InventorySlot var5;
                    int var12;
                    if ((var12 = (Integer)var10.next()) != this.getSelectedSlot() && (var5 = var8.getSlot(var12).getCompatible(var9)) != null) {
                        var8.switchSlotsOrCombineClient(this.getSelectedSlot(), var12, var5.count());
                    }
                }
            }

        }
    }

    private void switchToLookAt() {
        Vector3f var1 = new Vector3f(Controller.getCamera().getPos());
        Vector3f var2 = new Vector3f(Controller.getCamera().getPos());
        Vector3f var3;
        (var3 = new Vector3f(Controller.getCamera().getForward())).scale(200.0F);
        var2.add(var3);
        CubeRayCastResult var6;
        (var6 = new CubeRayCastResult(var1, var2, (Object)null, new SegmentController[0])).setIgnoereNotPhysical(true);
        var6.setOnlyCubeMeshes(true);
        ClosestRayResultCallback var4;
        SegmentController var5;
        if ((var4 = ((PhysicsExt)this.getState().getPhysics()).testRayCollisionPoint(var1, var2, var6, false)) != null && var4.hasHit() && var6.getSegment() != null && (var5 = var6.getSegment().getSegmentController()) != this.getState().getCurrentPlayerObject() && this.getState().getCurrentPlayerObject() != null && this.getState().getCurrentPlayerObject() instanceof SegmentController && ((SegmentController)this.getState().getCurrentPlayerObject()).railController.isInAnyRailRelationWith(var5) && InShipControlManager.checkEnter(var5)) {
            InShipControlManager.switchEntered(var5);
        }

    }

    public void onSuspend(boolean var1) {
        super.onSuspend(var1);
        synchronized(this.getState()) {
            boolean var3;
            if (!(var3 = this.getState().isSynched())) {
                this.getState().setSynched();
            }

            if (var1) {
                this.getState().getPlayer().getNetworkObject().keyboardOfController.set(Short.valueOf((short)0));
            }

            if (!var3) {
                this.getState().setUnsynched();
            }

        }
    }

    public void onSwitch(boolean var1) {
        if (var1) {
            if (!this.inShipControlManager.isActive() && !this.playerCharacterManager.isActive() && !this.playerCharacterManager.isDelayedActive() && !this.inShipControlManager.isDelayedActive()) {
                assert this.getState().getCharacter() != null;

                this.playerCharacterManager.setDelayedActive(true);
            }
        } else {
            this.getState().getPlayer().getNetworkObject().keyboardOfController.set(Short.valueOf((short)0));
        }

        super.onSwitch(var1);
    }

    public void update(Timer var1) {
        super.update(var1);
        if (this.undoRedoCooldown >= 0.0F) {
            this.undoRedoCooldown -= var1.getDelta();
        }

        if (this.isInAnyStructureBuildMode()) {
            this.buildCommandManager.update(var1);
        } else {
            this.buildCommandManager.updateOnNotInBuildmode(var1);
        }

        if (this.getState().getController().getPlayerInputs().isEmpty()) {
            if (!KeyboardMappings.OBJECT_VIEW_CAM.isDown(this.getState()) || Controller.getCamera() != null && Controller.getCamera() instanceof ObjectViewerCamera) {
                if (!KeyboardMappings.OBJECT_VIEW_CAM.isDown(this.getState()) && this.lookCamera != null && Controller.getCamera() == this.lookCamera) {
                    System.err.println("REVERTED CAMERA");
                    Controller.setCamera(this.lastCamera);
                }
            } else {
                this.lastCamera = Controller.getCamera();
                this.lookCamera = new ObjectViewerCamera(this.getState(), new FixedViewer(this.getState().getCurrentPlayerObject()), this.getState().getCurrentPlayerObject());
                Controller.setCamera(this.lookCamera);
            }

            if (KeyboardMappings.OBJECT_VIEW_CAM.isDown(this.getState()) && Keyboard.isKeyDown(2)) {
                this.getState().getCurrentPlayerObject().setInvisibleNextDraw();
            }

            this.getState().getPlayer().handleLocalKeyboardInput();
        }

    }

    private void controlAI(KeyEventInterface var1) throws StateParameterNotFoundException, PlayerControlledTransformableNotFound, UnloadedAiEntityException {
        SimpleTransformableSendableObject var2 = getLookingAt(this.getState(), true);
        SegmentPiece var3 = null;
        if (this.getState().getCharacter().isConrolledByActivePlayer()) {
            System.err.println("[AIPLAYERINTERACTION] CHECK FOR NEAREST PIECE");
            var3 = this.getState().getCharacter().getNearestPiece(40.0F, true);
        } else {
            System.err.println("[AIPLAYERINTERACTION] NO CHECK FOR NEAREST PIECE");
        }

        Vector3i var4;
        switch(KeyboardMappings.getEventKeyRaw(var1)) {
            case 2:
                ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Idling", true);
                this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_33, new Object[]{this.selectedCrew.getRealName()}), 0.0F);
                return;
            case 3:
                if (var2 != null && var2 == this.selectedCrew) {
                    this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_34, new Object[]{this.selectedCrew.getRealName()}), 0.0F);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ATTACK_TARGET).switchSetting("none", true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Idling", true);
                    return;
                } else {
                    System.err.println("[PLAYERINTERACTION] AIselected: " + this.selectedSlot + " -> ATTACK: " + var2);
                    if (var2 != null) {
                        ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ATTACK_TARGET).switchSetting(var2.getUniqueIdentifier(), true);
                        ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Attacking", true);
                        this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_35, new Object[]{this.selectedCrew.getRealName(), var2.toNiceString()}), 0.0F);
                        return;
                    }

                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_36, 0.0F);
                    return;
                }
            case 4:
                if (var3 != null) {
                    var4 = var3.getAbsolutePos(new Vector3i());
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORIGIN_X).switchSetting(String.valueOf(var4.x), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORIGIN_Y).switchSetting(String.valueOf(var4.y), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORIGIN_Z).switchSetting(String.valueOf(var4.z), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Roaming", true);
                    this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_37, new Object[]{this.selectedCrew.getRealName(), var4, var3.getSegment().getSegmentController().toNiceString()}), 0.0F);
                    return;
                }

                ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Roaming", true);
                this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_38, new Object[]{this.selectedCrew.getRealName()}), 0.0F);
                return;
            case 5:
                ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.FOLLOW_TARGET).switchSetting(this.getState().getPlayer().getFirstControlledTransformable().getUniqueIdentifier(), true);
                ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("Following", true);
                this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_39, new Object[]{this.selectedCrew.getRealName(), this.getState().getPlayer().getFirstControlledTransformable().toNiceString()}), 0.0F);
                return;
            case 6:
                if (var3 != null) {
                    var4 = var3.getAbsolutePos(new Vector3i());
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.TARGET_AFFINITY).switchSetting(String.valueOf(var3.getSegment().getSegmentController().getUniqueIdentifier()), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.TARGET_X).switchSetting(String.valueOf(var4.x - 16), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.TARGET_Y).switchSetting(String.valueOf(var4.y - 16), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.TARGET_Z).switchSetting(String.valueOf(var4.z - 16), true);
                    ((AIGameConfiguration)this.selectedCrew.getAi().getAiConfiguration()).get(Types.ORDER).switchSetting("GoTo", true);
                    this.getState().getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_40, new Object[]{this.selectedCrew.getRealName(), var4, var3.getSegment().getSegmentController().toNiceString()}), 0.0F);
                    return;
                } else {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_41, 0.0F);
                }
            default:
        }
    }

    private void checkAI(KeyEventInterface var1) {
        int var2 = KeyboardMappings.getEventKeySingle(var1) - 2;
        System.err.println("[PLAYERINTERACTION] CHECK AI SLOT: " + var2 + " selected: " + this.selectedSlot);
        if (this.selectedCrew == null) {
            if (var2 >= 0 && var2 < 5) {
                List var6 = this.getState().getPlayer().getPlayerAiManager().getCrew();
                if (var2 < var6.size()) {
                    this.selectedCrew = (AiInterfaceContainer)var6.get(var2);
                } else {
                    this.selectedCrew = null;
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_42, 0.0F);
                }
            } else {
                this.selectedCrew = null;
            }
        } else {
            try {
                if (var2 >= 0 && var2 < 5) {
                    try {
                        this.controlAI(var1);
                    } catch (UnloadedAiEntityException var3) {
                        var3.printStackTrace();
                        this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_43 + var3.uid, 0.0F);
                        this.selectedCrew = null;
                    }
                } else {
                    this.selectedCrew = null;
                }
            } catch (StateParameterNotFoundException var4) {
                var4.printStackTrace();
            } catch (PlayerControlledTransformableNotFound var5) {
                var5.printStackTrace();
            }
        }
    }

    public void handleKeyOrientation(KeyEventInterface var1) {
        Controller.getCamera().getWorldTransform();
        if (this.getState().getShip() != null) {
            this.getState().getShip().getWorldTransform();
        } else {
            (new Transform()).setIdentity();
        }

        if (KeyboardMappings.getEventKeySingle(var1) == 52) {
            int var2 = GUIOrientationSettingElement.getMaxRotation(this);
            this.setBlockOrientation(FastMath.cyclicModulo(this.getBlockOrientation() + 1, var2));
            short var3;
            ElementInformation var4;
            if ((var3 = this.getSelectedType()) != 0 && !(var4 = ElementKeyMap.getInfo(var3)).isNormalBlockStyle()) {
                System.err.println("BLOCK ORIENTATION: " + this.getBlockOrientation() + "; " + BlockShapeAlgorithm.algorithms[var4.getBlockStyle().id - 1][this.getBlockOrientation()].toString());
            }
        }

    }

    public void checkOrienationForNewSelectedSlot() {
        if (this.lastSelectedType != this.getSelectedTypeWithSub() && this.getSelectedTypeWithSub() > 0) {
            ElementInformation var1 = ElementKeyMap.getInfo(this.getSelectedTypeWithSub());
            if (this.lastSelectedType <= 0 || !var1.isOrientatable() && var1.isNormalBlockStyle()) {
                int var4 = this.getBlockOrientation();
                if (this.blockStyleMap.containsKey(var1.getBlockStyle())) {
                    this.setBlockOrientation(this.blockStyleMap.get(var1.getBlockStyle()));
                } else {
                    this.setBlockOrientation(var1.getDefaultOrientation());
                }

                ElementInformation var5;
                if (ElementKeyMap.isValidType(this.lastSelectedType) && (var5 = ElementKeyMap.getInfo(this.lastSelectedType)).getBlockStyle() != BlockStyle.NORMAL) {
                    this.blockStyleMap.put(var5.getBlockStyle(), var4);
                }
            } else {
                ElementInformation var2 = ElementKeyMap.getInfo(this.lastSelectedType);
                int var3 = this.getBlockOrientation();
                if (var1.getBlockStyle() == BlockStyle.NORMAL) {
                    if (var1.getSlab() != 0) {
                        this.setBlockOrientation(this.lastSelectedSlabOrient);
                    } else {
                        this.setBlockOrientation(var1.getDefaultOrientation());
                    }
                } else if (var1.getBlockStyle() != var2.getBlockStyle()) {
                    if (this.blockStyleMap.containsKey(var1.getBlockStyle())) {
                        this.setBlockOrientation(this.blockStyleMap.get(var1.getBlockStyle()));
                    } else {
                        this.blockStyleMap.put(var2.getBlockStyle(), this.getBlockOrientation());
                    }
                }

                if (var2.getBlockStyle() != BlockStyle.NORMAL) {
                    this.blockStyleMap.put(var2.getBlockStyle(), var3);
                }
            }
        }

        if (this.getSelectedTypeWithSub() > 0) {
            this.lastSelectedType = this.getSelectedTypeWithSub();
            if (ElementKeyMap.isValidType(this.lastSelectedType) && ElementKeyMap.getInfoFast(this.lastSelectedType).getSlab() > 0) {
                this.lastSelectedSlabOrient = this.getBlockOrientation();
            }
        }

    }

    public boolean handleSlotKey(KeyEventInterface var1, int var2) {
        if (!KeyboardMappings.CREW_CONTROL.isDown(this.getState()) && this.slotKeyMap.containsKey(KeyboardMappings.getEventKeySingle(var1))) {
            short var5 = this.getSelectedType();
            boolean var3 = this.selectedSlot == (Integer)this.slotKeyMap.get(KeyboardMappings.getEventKeySingle(var1));
            this.selectedSlot = (Integer)this.slotKeyMap.get(KeyboardMappings.getEventKeySingle(var1));
            if (this.getForcedSelect() != 0) {
                this.forcedSelect = 0;
            }

            boolean var4 = false;
            if (ElementKeyMap.isValidType(var5) && ElementKeyMap.getInfoFast(var5).blocktypeIds != null) {
                var4 = true;
            }

            if (!var3 || this.getSelectedType() != -32768 && !var4) {
                this.selectedSubSlot = 0;
            } else {
                InventorySlot var6;
                if ((var6 = this.getState().getPlayer().getInventory().getSlot(this.selectedSlot)) != null && var6.isMultiSlot()) {
                    this.selectedSubSlot = FastMath.cyclicModulo(this.selectedSubSlot + 1, var6.getSubSlots().size());
                } else if (var6 != null && var6.getType() > 0 && ElementKeyMap.getInfoFast(var6.getType()).blocktypeIds != null) {
                    short[] var7 = this.getState().getBlockSyleSubSlotController().getSelectedStack(var6.getType());
                    this.selectedSubSlot = FastMath.cyclicModulo(this.selectedSubSlot + 1, var7.length);
                } else {
                    this.selectedSubSlot = 0;
                }
            }

            this.getState().getPlayer().setSelectedBuildSlot(this.selectedSlot);
            this.checkOrienationForNewSelectedSlot();
            return true;
        } else {
            return false;
        }
    }

    public void initialize() {
        this.initializeKeyMap();
        this.inShipControlManager = new InShipControlManager(this.getState());
        this.playerCharacterManager = new PlayerExternalController(this.getState());
        this.segmentControlManager = new SegmentControlManager(this.getState());
        this.buildToolsManager = new BuildToolsManager(this.getState());
        this.hotbarLayout = new HotbarLayout(this.getState());
        this.getControlManagers().add(this.buildToolsManager);
        this.getControlManagers().add(this.inShipControlManager);
        this.getControlManagers().add(this.playerCharacterManager);
        this.getControlManagers().add(this.segmentControlManager);
    }

    public void initializeKeyMap() {
        this.slotKeyMap = new HashMap();
        this.slotKeyMap.put(2, 0);
        this.slotKeyMap.put(3, 1);
        this.slotKeyMap.put(4, 2);
        this.slotKeyMap.put(5, 3);
        this.slotKeyMap.put(6, 4);
        this.slotKeyMap.put(7, 5);
        this.slotKeyMap.put(8, 6);
        this.slotKeyMap.put(9, 7);
        this.slotKeyMap.put(10, 8);
        this.slotKeyMap.put(11, 9);
    }

    public void removeBlock(final EditableSendableSegmentController var1, Vector3f var2, Vector3f var3, final SegmentPiece var4, float var5, SymmetryPlanes var6, short var7, final RemoveCallback var8) {
        if (this.getState().getController().allowedToEdit(var1)) {
            if (var1.isInTestSector()) {
                this.getState().getController().popupInfoTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_64, 0.0F);
            } else if (!this.isAllowedToBuildAndSpawnShips()) {
                this.getState().getController().popupAlertTextMessage("ERROR\n \nCan't do that!\nYou are a spectator!", 0.0F);
            } else {
                for (SymmetryPlane symmetryPlane : BetterBuilding.getInstance().getAllPlanes()) {
                    short var9 = 0;
                    if (var7 == 32767 && this.getBuildToolsManager().getRemoveFilter() != 0) {
                        var7 = this.getBuildToolsManager().getRemoveFilter();
                        short var10;
                        if (this.getBuildToolsManager().isReplaceRemoveFilter() && ElementKeyMap.isValidType(var10 = this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedTypeWithSub())) {
                            System.err.println("[CLIENT] Replace filter: replace: " + ElementKeyMap.toString(var7) + " with " + ElementKeyMap.toString(var10));
                            var9 = var10;
                        }
                    }

                    BuildHelper var13 = null;
                    if (this.getBuildToolsManager().getBuildHelper() != null && this.getBuildToolsManager().buildHelperReplace) {
                        var13 = this.getBuildToolsManager().getBuildHelper();
                    }

                    Vector3i var11 = new Vector3i(1, 1, 1);
                    if (isAdvancedBuildMode(this.getState())) {
                        var11.set(this.getBuildToolsManager().getSize());
                    }

                    if (var1.getHpController().isRebooting()) {
                        this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_55, new Object[]{StringTools.formatTimeFromMS(var1.getHpController().getRebootTimeLeftMS())}), 0.0F);
                    } else {
                        if (var1.hasStructureAndArmorHP() && var1.getHpController().getHpPercent() < 1.0D && !this.warned.contains(var1.getId())) {
                            this.warned.add(var1.getId());
                            PlayerGameOkCancelInput var12;
                            (var12 = new PlayerGameOkCancelInput("CONFIRM", this.getState(), Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_56, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_44) {
                                public boolean isOccluded() {
                                    return false;
                                }

                                public void onDeactivate() {
                                }

                                public void pressedOK() {
                                    this.deactivate();
                                    PlayerInteractionControlManager.this.getInShipControlManager().popupShipRebootDialog(var1);
                                }
                            }).getInputPanel().onInit();
                            var12.getInputPanel().setOkButtonText(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_57);
                            var12.getInputPanel().setCancelButtonText(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_50);
                            var12.activate();
                        }

                        if (var1.isScrap()) {
                            this.getState().getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_59, new Object[]{KeyboardMappings.SPAWN_SPACE_STATION.getKeyChar()}), 0.0F);
                        }

                        BuildInstruction var14 = new BuildInstruction(var1);
                        this.moddedSegs.clear();
                        var1.getNearestIntersectingElementPosition(var2, var3, var11, var5, new BuildRemoveCallback() {
                            public void onRemove(long var1x, short var3) {
                                var8.onRemove(var1x, var3);
                            }

                            public boolean canRemove(short var1x) {
                                ElementInformation var2;
                                if (ElementKeyMap.isValidType(var1x) && ((var2 = ElementKeyMap.getInfo(var1x)).isReactorChamberAny() || var2.getId() == 1008) && var1 instanceof ManagedSegmentController && ((ManagedSegmentController) var1).getManagerContainer().getPowerInterface().isAnyRebooting()) {
                                    PlayerInteractionControlManager.this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_67);
                                    return false;
                                } else {
                                    boolean var3;
                                    if (!(var3 = PlayerInteractionControlManager.this.getState().getPlayer().getInventory().canPutIn(var1x, 1))) {
                                        PlayerInteractionControlManager.this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_60, 0.0F);
                                    }

                                    return var3;
                                }
                            }

                            public long getSelectedControllerPos() {
                                return var4 != null ? var4.getAbsoluteIndex() : -9223372036854775808L;
                            }
                        }, symmetryPlane, var7, var9, var13, var14, this.moddedSegs);
                        this.moddedSegs.clear();
                        this.getState().getController().queueUIAudio("0022_action - buttons push medium");
                        this.undo.add(0, var14);

                        while (this.undo.size() > (Integer) EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                            this.undo.remove(this.undo.size() - 1);
                        }

                        this.redo.clear();
                    }
                }
            }
        }

    }

    private void selectEntity(int var1) {
        ObjectIterator var2 = this.getState().getCurrentSectorEntities().values().iterator();
        int var3 = var1 > 0 ? 2147483647 : -2147483648;
        int var4 = this.selectedEntity != null ? this.selectedEntity.getId() : -1;

        SimpleTransformableSendableObject var5;
        while(var2.hasNext()) {
            var5 = (SimpleTransformableSendableObject)((Sendable)var2.next());
            if (this.selectedEntity == null) {
                this.selectEntityMax(-var1);
                return;
            }

            if (var1 > 0 && var5.getId() > var4 && var5.getId() < var3) {
                var3 = var5.getId();
            }

            if (var1 <= 0 && var5.getId() < var4 && var5.getId() > var3) {
                var3 = var5.getId();
            }
        }

        if (var3 != 2147483647 && var3 != -2147483648) {
            if (this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().containsKey(var3)) {
                var5 = (SimpleTransformableSendableObject)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var3);
                this.setSelectedEntity(var5);
            }

        } else {
            this.selectEntityMax(var1);
        }
    }

    private void selectEntityMax(int var1) {
        ObjectIterator var2 = this.getState().getCurrentSectorEntities().values().iterator();
        int var3 = var1 > 0 ? 2147483647 : -2147483648;

        SimpleTransformableSendableObject var4;
        while(var2.hasNext()) {
            var4 = (SimpleTransformableSendableObject)var2.next();
            if (var1 > 0 && var4.getId() < var3) {
                var3 = var4.getId();
            }

            if (var1 <= 0 && var4.getId() > var3) {
                var3 = var4.getId();
            }
        }

        if (this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().containsKey(var3)) {
            var4 = (SimpleTransformableSendableObject)this.getState().getLocalAndRemoteObjectContainer().getLocalObjects().get(var3);
            this.setSelectedEntity(var4);
        }

    }

    public NavigationControllerManager getNavigationControllerManager() {
        return this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getNavigationControlManager();
    }

    public static SimpleTransformableSendableObject<?> getLookingAt(GameClientState var0, boolean var1) {
        return getLookingAt(var0, var1, 250.0F, false, 0.0F, false);
    }

    public static SimpleTransformableSendableObject<?> getLookingAt(GameClientState var0, boolean var1, float var2, boolean var3, float var4, boolean var5) {
        if (var0.getScene() != null && var0.getScene().getWorldToScreenConverter() != null) {
            WorldToScreenConverter var6 = var0.getScene().getWorldToScreenConverter();
            Vector3f var7 = new Vector3f();
            Vector3f var8 = new Vector3f();
            float var9 = 0.0F;
            float var10 = -1.0F;
            SimpleTransformableSendableObject var11 = null;
            Vector3f var12 = var6.getMiddleOfScreen(new Vector3f());
            Iterator var13 = var0.getCurrentSectorEntities().values().iterator();

            while(true) {
                SimpleTransformableSendableObject var14;
                Vector3f var17;
                Vector3f var18;
                do {
                    do {
                        do {
                            PlayerInteractionControlManager var15;
                            do {
                                do {
                                    do {
                                        if (!var13.hasNext()) {
                                            return var11;
                                        }
                                    } while((var14 = (SimpleTransformableSendableObject)var13.next()) == var0.getCurrentPlayerObject());
                                } while(var14.isHidden());

                                var15 = var0.getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
                            } while(var1 && !var15.getNavigationControllerManager().isDisplayed(var14));

                            if (var0.isInCharacterBuildMode() && var0.getCharacter().getGravity().source == var14) {
                                where.set(var14.getWorldTransformOnClient());
                            } else {
                                var14.getWorldTransformOnClientCenterOfMass(where);
                            }

                            var8.set(where.origin);
                            var8.sub(Controller.getCamera().getPos());
                            var8.normalize();
                            (var18 = Controller.getCamera().getForward()).normalize();
                            Vector3f var16 = var6.convert(where.origin, new Vector3f(), true);
                            (var17 = new Vector3f()).sub(var16, var12);
                            var7.sub(var0.getCurrentPlayerObject().getWorldTransformOnClient().origin, where.origin);
                        } while(var17.length() >= var2);
                    } while(var3 && var18.dot(var8) <= var4);
                } while(var11 != null && (var17.length() >= var9 || var5 && var7.length() >= var10));

                var11 = var14;
                var9 = var17.length();
                var10 = var7.length();
            }
        } else {
            return null;
        }
    }

    private void selectLookingAt() {
        this.setSelectedEntity(getLookingAt(this.getState(), true));
    }

    private void selectNearestEntity() {
        Vector3f var1 = new Vector3f();
        Vector3f var2 = new Vector3f();
        float var3 = 0.0F;
        if (this.getState().getCurrentPlayerObject() != null) {
            var1.set(this.getState().getCurrentPlayerObject().getWorldTransform().origin);
        } else {
            var1.set(Controller.getCamera().getPos());
        }

        SimpleTransformableSendableObject var4 = null;
        Iterator var5 = this.getState().getCurrentSectorEntities().values().iterator();

        while(true) {
            SimpleTransformableSendableObject var6;
            do {
                do {
                    do {
                        if (!var5.hasNext()) {
                            this.setSelectedEntity(var4);
                            return;
                        }

                        var6 = (SimpleTransformableSendableObject)var5.next();
                        var2.sub(var6.getWorldTransformOnClient().origin, var1);
                    } while(var6 == this.getState().getCurrentPlayerObject());
                } while(var6 == this.getState().getPlayer().getAbstractCharacterObject());
            } while(var4 != null && var2.length() >= var3);

            System.err.println("!!!!!!!!!NEAREST IS NOW " + var6);
            var4 = var6;
            var3 = var2.length();
        }
    }

    public AiInterfaceContainer getSelectedCrew() {
        return this.selectedCrew;
    }

    public void setSelectedCrew(AiInterfaceContainer var1) {
        this.selectedCrew = var1;
    }

    public SegmentPiece getEntered() {
        return this.entered;
    }

    public void setEntered(SegmentPiece var1) {
        System.err.println("[CLIENT] CHANGED ENTERED: set to: " + var1);
        this.entered = var1;
        if (var1 == null) {
            this.getState().getWorldDrawer().flagJustEntered((SegmentController)null);
        }

    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    public void undo() {
        try {
            if ((this.undoRedoCooldown < 0.0F || GameServerState.isCreated()) && this.entered != null && this.undo.size() > 0 && ((BuildInstruction)this.undo.get(0)).getController() == this.entered.getSegment().getSegmentController()) {
                BuildInstruction var1 = (BuildInstruction)this.undo.remove(0);
                BuildInstruction var2;
                (var2 = new BuildInstruction(var1.getController())).fillTool = var1.fillTool;
                this.undoRedoCooldown = Math.min(5.0F, (float)(var1.getAdds().size() + var1.getRemoves().size() + (var1.getReplaces().size() << 1)) * 5.0E-4F);
                ((EditableSendableSegmentController)var1.getController()).undo(var1, var2);
                this.redo.add(0, var1);
                System.err.println("[UNDO] ADDING REDO " + var1);

                while(this.redo.size() > (Integer)EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                    this.redo.remove(this.redo.size() - 1);
                }

            } else {
                if (this.undoRedoCooldown >= 0.0F) {
                    this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_78);
                }

                System.err.println("[UNDO] CANNOT UNDO AT THE MOMENT: " + (this.undoRedoCooldown < 0.0F) + ", entered: " + (this.entered != null) + ", undoavail: " + this.undo.size() + " - " + (this.undo.size() > 0) + ", contr: " + (((BuildInstruction)this.undo.get(0)).getController() == this.entered.getSegment().getSegmentController()));
            }
        } catch (Exception var3) {
            System.err.println("ERROR IN REDO");
            var3.printStackTrace();
        }
    }

    public void redo() {
        try {
            if (this.undoRedoCooldown < 0.0F && this.entered != null && this.redo.size() > 0 && ((BuildInstruction)this.redo.get(0)).getController() == this.entered.getSegment().getSegmentController()) {
                BuildInstruction var1 = (BuildInstruction)this.redo.remove(0);
                BuildInstruction var2;
                (var2 = new BuildInstruction(var1.getController())).fillTool = var1.fillTool;
                this.undoRedoCooldown = Math.min(5.0F, (float)(var1.getAdds().size() + var1.getRemoves().size() + (var1.getReplaces().size() << 1)) * 5.0E-4F);
                ((EditableSendableSegmentController)var1.getController()).redo(var1, var2);
                this.undo.add(0, var2);
                System.err.println("[REDO] ADDING UNDO " + var1);

                while(this.undo.size() > (Integer)EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                    this.undo.remove(this.undo.size() - 1);
                }
            }

        } catch (Exception var3) {
            System.err.println("ERROR IN REDO");
            var3.printStackTrace();
        }
    }

    public boolean isUndoRedoOnCooldown() {
        return this.undoRedoCooldown >= 0.0F;
    }

    public short getSelectedTypeWithSub() {
        if (this.getForcedSelect() != 0) {
            return this.getForcedSelect();
        } else {
            InventorySlot var1 = this.getState().getPlayer().getInventory().getSlot(this.getSelectedSlot());
            boolean var2 = false;
            short var3;
            if (var1 != null && var1.isMultiSlot() && this.getSelectedSubSlot() >= 0 && this.getSelectedSubSlot() < var1.getSubSlots().size()) {
                var3 = ((InventorySlot)var1.getSubSlots().get(this.getSelectedSubSlot())).getType();
            } else if (var1 != null && var1.getType() > 0 && ElementKeyMap.getInfoFast(var1.getType()).blocktypeIds != null) {
                var2 = true;
                if (this.getSelectedSubSlot() >= this.getState().getBlockSyleSubSlotController().getSelectedStack(var1.getType()).length) {
                    this.resetSubSlot();
                }

                var3 = this.getState().getBlockSyleSubSlotController().getBlockType(var1.getType(), this.getSelectedSubSlot());
            } else {
                var3 = this.getSelectedType();
            }

            if (ElementKeyMap.isValidType(var3) && !var2 && ElementKeyMap.getInfoFast(var3).slabIds != null && this.buildToolsManager.slabSize.setting > 0) {
                var3 = ElementKeyMap.getInfoFast(var3).slabIds[this.buildToolsManager.slabSize.setting - 1];
            }

            return var3;
        }
    }

    public boolean isMultiSlot() {
        InventorySlot var1;
        return (var1 = this.getState().getPlayer().getInventory().getSlot(this.getSelectedSlot())) != null && var1.getType() == -32768;
    }

    public void setSelectedBlockByActiveController(SegmentPiece var1) {
        if (this.getPlayerCharacterManager().isTreeActive() && !this.getPlayerCharacterManager().isSuspended()) {
            this.getPlayerCharacterManager().setSelectedBlock(var1);
        } else if (this.getInShipControlManager().getShipControlManager().getSegmentBuildController().isTreeActive()) {
            this.getInShipControlManager().getShipControlManager().getSegmentBuildController().setSelectedBlock(var1);
        } else {
            this.getSegmentControlManager().getSegmentBuildController().setSelectedBlock(var1);
        }
    }

    public SegmentPiece getSelectedBlockByActiveController() {
        if (this.getPlayerCharacterManager().isTreeActive() && !this.getPlayerCharacterManager().isSuspended()) {
            return this.getPlayerCharacterManager().getSelectedBlock();
        } else {
            return this.getInShipControlManager().getShipControlManager().getSegmentBuildController().isTreeActive() ? this.getInShipControlManager().getShipControlManager().getSegmentBuildController().getSelectedBlock() : this.getSegmentControlManager().getSegmentBuildController().getSelectedBlock();
        }
    }

    public boolean activateResetFactionIfOwner(final SegmentController var1) {
        if (var1.isOwnerSpecific(this.getState().getPlayer())) {
            if (!var1.railController.isDockedAndExecuted()) {
                (new PlayerGameOkCancelInput("CONFIRM", this.getState(), Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_46, Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_47) {
                    public void pressedOK() {
                        this.getState().getPlayer().getFactionController().sendEntityFactionIdChangeRequest(this.getState().getPlayer().getFactionId(), var1);
                        this.deactivate();
                    }

                    public void onDeactivate() {
                    }
                }).activate();
                return true;
            }

            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_PLAYERINTERACTIONCONTROLMANAGER_45, 0.0F);
        }

        return false;
    }

    public void removeLayer(int var1, int var2, int var3) {
        SimpleTransformableSendableObject var4;
        if ((var4 = this.getSelectedEntity()) != null && var4 instanceof SegmentController && this.getState().getPlayer().isAdmin()) {
            EditableSendableSegmentController var7;
            BoundingBox var5 = (var7 = (EditableSendableSegmentController)var4).getBoundingBox();
            Vector3f var10000 = (var5 = new BoundingBox(var5)).min;
            var10000.x += 17.0F;
            var10000 = var5.min;
            var10000.y += 17.0F;
            var10000 = var5.min;
            var10000.z += 17.0F;
            var10000 = var5.max;
            var10000.x += 15.0F;
            var10000 = var5.max;
            var10000.y += 15.0F;
            var10000 = var5.max;
            var10000.z += 15.0F;
            BoundingBox var6 = new BoundingBox(var5);
            System.err.println("[CLIENT] BB: " + var6 + "; " + var7);
            if (var1 > 0) {
                var6.min.x = var6.max.x - (float)Math.abs(var1);
                var6.max.x = var6.min.x + 1.0F;
            }

            if (var2 > 0) {
                var6.min.y = var6.max.y - (float)Math.abs(var2);
                var6.max.y = var6.min.y + 1.0F;
            }

            if (var3 > 0) {
                var6.min.z = var6.max.z - (float)Math.abs(var3);
                var6.max.z = var6.min.z + 1.0F;
            }

            if (var1 < 0) {
                var6.max.x = var6.min.x + (float)Math.abs(var1);
                var6.min.x = var6.max.x - 1.0F;
            }

            if (var2 < 0) {
                var6.max.y = var6.min.y + (float)Math.abs(var2);
                var6.min.y = var6.max.y - 1.0F;
            }

            if (var3 < 0) {
                var6.max.z = var6.min.z + (float)Math.abs(var3);
                var6.min.z = var6.max.z - 1.0F;
            }

            if (!var6.isValidIncludingZero() || !var6.intersects(var5)) {
                return;
            }

            if (this.removeLayer(var6, var7) == 0) {
                System.err.println("LAYER ::: " + var6);
                this.removeLayer(var1 + FastMath.sign(var1), var2 + FastMath.sign(var2), var3 + FastMath.sign(var3));
            }
        }

    }

    public int removeLayer(BoundingBox var1, EditableSendableSegmentController var2) {
        ObjectOpenHashSet var3 = new ObjectOpenHashSet(16);
        BuildInstruction var4 = new BuildInstruction(var2);

        for(int var5 = (int)var1.min.z; (float)var5 < var1.max.z; ++var5) {
            for(int var6 = (int)var1.min.y; (float)var6 < var1.max.y; ++var6) {
                for(int var7 = (int)var1.min.x; (float)var7 < var1.max.x; ++var7) {
                    var2.remove(var7, var6, var5, new BuildRemoveCallback() {
                        public long getSelectedControllerPos() {
                            return -9223372036854775808L;
                        }

                        public void onRemove(long var1, short var3) {
                        }

                        public boolean canRemove(short var1) {
                            return true;
                        }
                    }, true, var3, (short)32767, (short)0, this.getBlockOrientation(), (BuildHelper)null, var4);
                }
            }
        }

        Iterator var8 = var3.iterator();

        while(var8.hasNext()) {
            Segment var9;
            if (!(var9 = (Segment)var8.next()).isEmpty()) {
                var9.getSegmentData().restructBB(true);
            } else {
                var2.getSegmentBuffer().restructBB();
            }
        }

        if (var4.getRemoves().size() > 0) {
            this.undo.add(0, var4);

            while(this.undo.size() > (Integer)EngineSettings.B_UNDO_REDO_MAX.getCurrentState()) {
                this.undo.remove(this.undo.size() - 1);
            }

            this.redo.clear();
        }

        return var4.getRemoves().size();
    }

    public void debugPush() {
        SimpleTransformableSendableObject var1;
        if ((var1 = this.getSelectedEntity()) != null && var1 instanceof SegmentController) {
            RigidBody var2;
            (var2 = (RigidBody)var1.getPhysicsDataContainer().getObject()).setLinearVelocity(new Vector3f(0.0F, 100.0F, 1.0F));
            var2.setAngularVelocity(new Vector3f(0.0F, 30.0F, 0.0F));
        }

    }

    public boolean checkRadialSelect(short var1) {
        if (this.forcedSelect != 0) {
            return false;
        } else if (ElementKeyMap.isValidType(var1) && ElementKeyMap.getInfoFast(var1).blocktypeIds != null) {
            if (this.getSelectedSubSlot() < this.getState().getBlockSyleSubSlotController().getSelectedStack(var1).length && this.getState().getBlockSyleSubSlotController().getBlockType(var1, this.getSelectedSubSlot()) < 0) {
                (new RadialMenuDialogShapes(this.getState(), ElementKeyMap.getInfo(var1))).activate();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void openRadialSelectShapes(short var1) {
        if (ElementKeyMap.isValidType(var1)) {
            ElementInformation var3 = ElementKeyMap.getInfoFast(var1);
            System.out.println("RADIAL radial shape open for " + var3.getName());
            if (var3.blocktypeIds != null || var3.getSourceReference() != 0) {
                var3 = var3.getSourceReference() != 0 ? ElementKeyMap.getInfoFast(var3.getSourceReference()) : var3;
                RadialMenuDialogShapes var2 = new RadialMenuDialogShapes(this.getState(), var3);
                System.out.println("RADIAL creating radial menu for " + var3.getName());
                var2.activate();
            }
        }

    }

    public void selectTypeForced(short var1) {
        this.forcedSelect = var1;
    }

    public short getForcedSelect() {
        return this.forcedSelect;
    }

    public void resetSubSlot() {
        this.selectedSubSlot = 0;
    }

    public List<BuildInstruction> getUndo() {
        return this.undo;
    }

    public boolean isStickyAdvBuildMode() {
        return this.stickyAdvBuildMode;
    }

    public void setStickyAdvBuildMode(boolean var1) {
        this.stickyAdvBuildMode = var1;
    }

    public BuildConstructionManager getBuildCommandManager() {
        return this.buildCommandManager;
    }

    public void promptBuild(short var1, int var2, String var3) {
        BuildConstructionCommand var4;
        (var4 = new BuildConstructionCommand(this.buildCommandManager, var1, var2)).setInstruction(var3);
        this.buildCommandManager.enqueue(var4);
    }

    public boolean canQueue(short var1, int var2) {
        BuildConstructionCommand var3 = new BuildConstructionCommand(this.buildCommandManager, var1, var2);
        return this.buildCommandManager.canQueue(var3);
    }

    public void resetQueue() {
        this.buildCommandManager.resetQueue();
    }

    interface SurroundBlockCondition {
        boolean ok(ElementInformation var1);
    }
}