package org.schema.game.client.controller.manager.ingame;

import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.FastMath;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.buildhelper.BuildHelper;
import org.schema.game.client.view.cubes.shapes.BlockShapeAlgorithm;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.client.view.tools.SingleBlockDrawer;
import org.schema.game.common.controller.EditableSendableSegmentController;
import org.schema.game.common.controller.PositionBlockedException;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.element.ElementInformation.ResourceInjectionType;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.world.SegmentData3Byte;
import org.schema.game.common.util.FastCopyLongOpenHashSet;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.resource.FileExt;

/**
 * CopyArea.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public class CopyArea {
    public static final String path = "./templates/";
    private static final int VERSION = 5;
    private final ObjectArrayList<VoidSegmentPiece> pieces = new ObjectArrayList();
    private final Long2ObjectOpenHashMap<LongArrayList> connections = new Long2ObjectOpenHashMap();
    private final Long2ObjectOpenHashMap<String> textMap = new Long2ObjectOpenHashMap();
    private final Long2ObjectOpenHashMap<Short2IntOpenHashMap> inventoryFilters = new Long2ObjectOpenHashMap();
    private final Long2ObjectOpenHashMap<Short2IntOpenHashMap> inventoryFillUpFilters = new Long2ObjectOpenHashMap();
    private final Long2ShortOpenHashMap inventoryProduction = new Long2ShortOpenHashMap();
    private final Long2IntOpenHashMap inventoryProductionLimit = new Long2IntOpenHashMap();
    SingleBlockDrawer drawer = new SingleBlockDrawer();
    Transform t = new Transform();
    private Vector3i min;
    private Vector3i max;

    public CopyArea() {
    }

    public static int getRotOrienation(short var0, int var1, int var2, int var3, int var4, Matrix3f var5) {
        if (var0 != 0) {
            ElementInformation var6;
            if ((var6 = ElementKeyMap.getInfo(var0)).getBlockStyle() != BlockStyle.NORMAL && var6.getBlockStyle() != BlockStyle.SPRITE) {
                BlockShapeAlgorithm var9;
                var1 = (var9 = BlockShapeAlgorithm.getAlgo(var6.getBlockStyle(), (byte)var1)).findRot(BlockShapeAlgorithm.algorithms[var6.getBlockStyle().id - 1], var5);

                assert BlockShapeAlgorithm.algorithms[var6.getBlockStyle().id - 1][var1] != var9;
            } else {
                try {
                    if (var1 >= 6) {
                        try {
                            throw new RuntimeException("normal block had illegal rotation (only [0,5] allowed): " + var1);
                        } catch (RuntimeException var7) {
                            var7.printStackTrace();
                        }
                    }

                    var1 %= 6;
                    if (var2 == 1) {
                        var1 = Element.getClockWiseX(var1);
                    } else if (var2 == -1) {
                        var1 = Element.getCounterClockWiseX(var1);
                    } else if (var3 == 1) {
                        var1 = Element.getClockWiseY(var1);
                    } else if (var3 == -1) {
                        var1 = Element.getCounterClockWiseY(var1);
                    } else if (var4 == 1) {
                        var1 = Element.getClockWiseZ(var1);
                    } else if (var4 == -1) {
                        var1 = Element.getCounterClockWiseZ(var1);
                    }
                } catch (RuntimeException var8) {
                    System.err.println("Exception on rotating: " + ElementKeyMap.toString(var0));
                    throw var8;
                }
            }
        }

        return var1;
    }

    public void rotate(int var1, int var2, int var3) {
        Matrix3f var4 = new Matrix3f();
        if (var1 != 0) {
            var4.rotX(1.5707964F * (float)var1);
        } else if (var2 != 0) {
            var4.rotY(1.5707964F * (float)var2);
        } else if (var3 != 0) {
            var4.rotZ(1.5707964F * (float)var3);
        }

        Vector3f var5 = new Vector3f((float)this.min.x, (float)this.min.y, (float)this.min.z);
        Vector3f var6 = new Vector3f((float)this.max.x, (float)this.max.y, (float)this.max.z);
        var4.transform(var5);
        var4.transform(var6);
        Vector3i var7 = new Vector3i(FastMath.round(Math.min(var5.x, var6.x)), FastMath.round(Math.min(var5.y, var6.y)), FastMath.round(Math.min(var5.z, var6.z)));
        Vector3i var16 = new Vector3i(FastMath.round(Math.max(var5.x, var6.x)), FastMath.round(Math.max(var5.y, var6.y)), FastMath.round(Math.max(var5.z, var6.z)));
        var6 = new Vector3f();
        Long2ObjectOpenHashMap var8 = new Long2ObjectOpenHashMap(this.textMap.size());

        for(int var9 = 0; var9 < this.pieces.size(); ++var9) {
            VoidSegmentPiece var10 = (VoidSegmentPiece)this.pieces.get(var9);
            var6.set((float)var10.voidPos.x, (float)var10.voidPos.y, (float)var10.voidPos.z);
            var4.transform(var6);
            String var11 = null;
            if (this.textMap.containsKey(ElementCollection.getIndex(var10.voidPos))) {
                var11 = (String)this.textMap.get(ElementCollection.getIndex(var10.voidPos));
            }

            var10.voidPos.set(FastMath.round(var6.x), FastMath.round(var6.y), FastMath.round(var6.z));
            int var12 = getRotOrienation(var10.getType(), var10.getOrientation(), var1, var2, var3, var4);
            if (ElementKeyMap.getInfo(var10.getType()).getBlockStyle() != BlockStyle.NORMAL) {
                ElementKeyMap.getInfo(var10.getType()).getBlockStyle();
                BlockStyle var10000 = BlockStyle.SPRITE;
            }

            var10.setOrientation((byte)var12);
            if (var11 != null) {
                var8.put(ElementCollection.getIndex(var10.voidPos), var11);
            }
        }

        this.min.set(var7);
        this.max.set(var16);
        Long2ObjectOpenHashMap var19 = new Long2ObjectOpenHashMap(this.connections.size());
        Vector3f var20 = new Vector3f();
        Vector3i var21 = new Vector3i();
        Vector3f var22 = new Vector3f();
        Vector3i var13 = new Vector3i();
        Iterator var14 = this.connections.entrySet().iterator();

        while(var14.hasNext()) {
            Entry var15;
            ElementCollection.getPosFromIndex((Long)(var15 = (Entry)var14.next()).getKey(), var20);
            var4.transform(var20);
            var21.set(FastMath.round(var20.x), FastMath.round(var20.y), FastMath.round(var20.z));
            LongArrayList var17 = new LongArrayList(((LongArrayList)var15.getValue()).size());
            var19.put(ElementCollection.getIndex(var21), var17);

            for(int var18 = 0; var18 < ((LongArrayList)var15.getValue()).size(); ++var18) {
                ElementCollection.getPosFromIndex(((LongArrayList)var15.getValue()).getLong(var18), var22);
                var4.transform(var22);
                var13.set(FastMath.round(var22.x), FastMath.round(var22.y), FastMath.round(var22.z));
                var17.add(ElementCollection.getIndex(var13));
            }
        }

        this.connections.clear();
        this.connections.putAll(var19);
        this.textMap.clear();
        this.textMap.putAll(var8);
    }

    public void draw() {
        if (EngineSettings.G_DRAW_PASTE_PREVIEW.isOn()) {
            this.t.setIdentity();

            for(int var1 = 0; var1 < this.pieces.size(); ++var1) {
                VoidSegmentPiece var2;
                if (ElementKeyMap.isValidType((var2 = (VoidSegmentPiece)this.pieces.get(var1)).getType())) {
                    GlUtil.glPushMatrix();
                    GlUtil.translateModelview((float)var2.voidPos.x, (float)var2.voidPos.y, (float)var2.voidPos.z);
                    ElementInformation var3;
                    if ((var3 = ElementKeyMap.getInfo(var2.getType())).getBlockStyle() != BlockStyle.NORMAL) {
                        this.drawer.setSidedOrientation((byte)0);
                        this.drawer.setShapeOrientation24(BlockShapeAlgorithm.getLocalAlgoIndex(var3.getBlockStyle(), var2.getOrientation()));
                    } else if (var3.getIndividualSides() > 3) {
                        this.drawer.setShapeOrientation24((byte)0);
                        this.drawer.setSidedOrientation(var2.getOrientation());
                    } else if (var3.orientatable) {
                        this.drawer.setShapeOrientation24((byte)0);
                        this.drawer.setSidedOrientation(var2.getOrientation());
                    } else {
                        this.drawer.setShapeOrientation24((byte)0);
                        this.drawer.setSidedOrientation((byte)0);
                    }

                    this.drawer.alpha = 0.5F;
                    this.drawer.setActive(var2.isActive());
                    this.drawer.useSpriteIcons = false;
                    this.drawer.drawType(var2.getType(), this.t);
                    this.drawer.useSpriteIcons = true;
                    GlUtil.glPopMatrix();
                }
            }
        }

    }

    public void copyArea(SegmentController var1, Vector3i var2, Vector3i var3) {
        this.min = new Vector3i(var2);
        this.max = new Vector3i(var3);
        System.err.println("[CLIENT][COPYPASTE] RECORDING AREA");
        ManagerContainer var4 = null;
        if (var1 instanceof ManagedSegmentController) {
            var4 = ((ManagedSegmentController)var1).getManagerContainer();
        }

        for(int var5 = var2.z; var5 <= var3.z; ++var5) {
            for(int var6 = var2.y; var6 <= var3.y; ++var6) {
                for(int var7 = var2.x; var7 <= var3.x; ++var7) {
                    SegmentPiece var8;
                    if ((var8 = var1.getSegmentBuffer().getPointUnsave(ElementCollection.getIndex(var7, var6, var5))) != null && var8.getType() != 0) {
                        VoidSegmentPiece var9;
                        (var9 = new VoidSegmentPiece()).setDataByReference(var8.getData());
                        var8.getAbsolutePos(var9.voidPos);
                        var9.voidPos.sub(var2);
                        this.getPieces().add(var9);
                        var8.getType();
                        long var10 = ElementCollection.getIndex(var9.voidPos);
                        long var12 = ElementCollection.getIndex4((short)var7, (short)var6, (short)var5, (short)var9.getOrientation());
                        String var16;
                        if ((var16 = (String)var1.getTextMap().get(var12)) != null) {
                            this.textMap.put(var10, var16);
                        }

                        Inventory var17;
                        if (var4 != null && (var17 = var4.getInventory(var12)) != null) {
                            if (var17.getProduction() != 0) {
                                this.inventoryProduction.put(var10, var17.getProduction());
                            }

                            if (var17.getProductionLimit() != 0) {
                                this.inventoryProductionLimit.put(var10, var17.getProductionLimit());
                            }

                            if (var17.getFilter() != null) {
                                this.inventoryFilters.put(var10, var17.getFilter().filter.getMapInstance());
                                this.inventoryFillUpFilters.put(var10, var17.getFilter().fillUpTo.getMapInstance());
                            }
                        }

                        LongOpenHashSet var18;
                        if ((var18 = (LongOpenHashSet)var1.getControlElementMap().getControllingMap().getAll().get(ElementCollection.getIndex(var7, var6, var5))) != null) {
                            var9.senderId = -2;
                            Iterator var19 = var18.iterator();

                            while(var19.hasNext()) {
                                long var14;
                                int var20 = ElementCollection.getPosX(var14 = (Long)var19.next());
                                int var11 = ElementCollection.getPosY(var14);
                                int var21 = ElementCollection.getPosZ(var14);
                                if (var20 >= var2.x && var20 <= var3.x && var11 >= var2.y && var11 <= var3.y && var21 >= var2.z && var21 <= var3.z) {
                                    LongArrayList var13;
                                    if ((var13 = (LongArrayList)this.connections.get(ElementCollection.getIndex(var20 - var2.x, var11 - var2.y, var21 - var2.z))) == null) {
                                        var13 = new LongArrayList();
                                        this.connections.put(ElementCollection.getIndex(var20 - var2.x, var11 - var2.y, var21 - var2.z), var13);
                                    }

                                    var13.add(ElementCollection.getIndex(var9.voidPos));
                                }
                            }
                        }
                    }
                }
            }
        }

        Collections.sort(this.pieces, new Comparator<VoidSegmentPiece>() {
            public int compare(VoidSegmentPiece var1, VoidSegmentPiece var2) {
                return var1.senderId - var2.senderId;
            }
        });
        if (!this.getPieces().isEmpty()) {
            this.getPieces().trim();
        }

        System.err.println("[CLIENT][COPYPASTE] " + this.getPieces().size() + " blocks recorded");
    }

    public ObjectArrayList<VoidSegmentPiece> getPieces() {
        return this.pieces;
    }

    public Vector3i getSize() {
        return new Vector3i(this.max.x - this.min.x, this.max.y - this.min.y, this.max.z - this.min.z);
    }

    public Vector3f getSizef() {
        return new Vector3f((float)(this.max.x - this.min.x), (float)(this.max.y - this.min.y), (float)(this.max.z - this.min.z));
    }

    private boolean ok(EditableSendableSegmentController var1, VoidSegmentPiece var2) {
        if (var2.getType() == 291) {
            if (var1.getElementClassCountMap().get((short)291) > 0) {
                ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_0, 0.0F);
            }

            return false;
        } else if (var2.getType() == 121) {
            if (var1.getElementClassCountMap().get((short)121) > 0) {
                ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_1, 0.0F);
            }

            return false;
        } else if (var2.getType() == 347) {
            if (var1.getElementClassCountMap().get((short)347) > 0) {
                ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_2, 0.0F);
            }

            return false;
        } else if (var2.getType() == 1) {
            ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_3, 0.0F);
            return false;
        } else if (!var1.allowedType(var2.getType())) {
            ((GameClientState)var1.getState()).getController().popupAlertTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_4, new Object[]{ElementKeyMap.toString(var2.getType())}), 0.0F);
            return false;
        } else {
            return true;
        }
    }

    public void build(EditableSendableSegmentController var1, Vector3i var2, BuildInstruction var3, SymmetryPlane var4) {
        int var5 = ((GameClientState)var1.getState()).getGameState().getMaxBuildArea();
        Vector3f var6;
        if (!((var6 = this.getSizef()).x > (float)var5) && !(var6.y > (float)var5) && !(var6.z > (float)var5)) {
            CopyArea.BuildCB var7 = new CopyArea.BuildCB(var1);
            Vector3i var8 = new Vector3i();
            long var9 = System.currentTimeMillis();
            Short2IntOpenHashMap var11 = new Short2IntOpenHashMap();
            Vector3i var12 = new Vector3i(2147483647, 2147483647, 2147483647);
            Vector3i var13 = new Vector3i(-2147483648, -2147483648, -2147483648);
            Iterator var14 = this.pieces.iterator();

            while(var14.hasNext()) {
                VoidSegmentPiece var15 = (VoidSegmentPiece)var14.next();
                var11.add(var15.getType(), 1);
                var12.min(var15.voidPos.x, var15.voidPos.y, var15.voidPos.z);
                var13.max(var15.voidPos.x, var15.voidPos.y, var15.voidPos.z);
            }

            Vector3i var35;
            if ((var35 = new Vector3i(var13.x - var12.x, var13.y - var12.y, var13.z - var12.z)).x <= var5 && var35.y <= var5 && var35.z <= var5) {
                Iterator var36 = var11.short2IntEntrySet().iterator();

                while(var36.hasNext()) {
                    it.unimi.dsi.fastutil.shorts.Short2IntMap.Entry var16 = (it.unimi.dsi.fastutil.shorts.Short2IntMap.Entry)var36.next();
                    Inventory var17 = ((GameClientState)var1.getState()).getPlayer().getInventory();
                    short var18 = ElementKeyMap.convertSourceReference(var16.getShortKey());
                    if (var17.getOverallQuantity(var18) < var16.getIntValue()) {
                        ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_6, 0.0F);
                        break;
                    }
                }

                int[] var37 = new int[]{0, 2147483647};
                var1.dryBuildTest.boundingBox = new BoundingBox();

                Iterator var38;
                VoidSegmentPiece var40;
                try {
                    var38 = this.pieces.iterator();

                    while(var38.hasNext()) {
                        if (ElementKeyMap.getInfo((var40 = (VoidSegmentPiece)var38.next()).getType()).resourceInjection != ResourceInjectionType.OFF) {
                            var40.setOrientation((byte)0);
                        }

                        if (ElementKeyMap.getInfo(var40.getType()).isReactorChamberSpecific()) {
                            var40.setType((short)ElementKeyMap.getInfo(var40.getType()).chamberRoot);
                        }

                        var1.dryBuildTest.build(var2.x + var40.voidPos.x, var2.y + var40.voidPos.y, var2.z + var40.voidPos.z, var40.getType(), var40.getOrientation(), var40.isActive(), var7, var8, var37, (BuildHelper)null, var3);
                        var1.buildInSymmetry(var2.x + var40.voidPos.x, var2.y + var40.voidPos.y, var2.z + var40.voidPos.z, var40.getType(), var40.getOrientation(), var40.isActive(), var7, var8, var37, var3, (BuildHelper)null, var4, var1.dryBuildTest);
                    }
                } catch (PositionBlockedException var26) {
                    ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_12, 0.0F);
                    return;
                }

                Vector3f var10000 = var1.dryBuildTest.boundingBox.min;
                var10000.x -= 16.0F;
                var10000 = var1.dryBuildTest.boundingBox.min;
                var10000.y -= 16.0F;
                var10000 = var1.dryBuildTest.boundingBox.min;
                var10000.z -= 16.0F;
                var10000 = var1.dryBuildTest.boundingBox.max;
                var10000.x -= 16.0F;
                var10000 = var1.dryBuildTest.boundingBox.max;
                var10000.y -= 16.0F;
                var10000 = var1.dryBuildTest.boundingBox.max;
                var10000.z -= 16.0F;
                if (!ServerConfig.ALLOW_PASTE_AABB_OVERLAPPING.isOn() && var1.getCollisionChecker().checkAABBCollisionWithUnrelatedStructures(var1.getWorldTransformOnClient(), var1.dryBuildTest.boundingBox.min, var1.dryBuildTest.boundingBox.max, 0.1F)) {
                    System.err.println("[CLIENT] Overlapping paste BB. " + var1 + "; " + var1.dryBuildTest.boundingBox);
                    ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_13, 0.0F);
                } else {
                    var38 = this.pieces.iterator();

                    while(var38.hasNext()) {
                        var40 = (VoidSegmentPiece)var38.next();
                        if (this.ok(var1, var40) && ElementKeyMap.isValidType(var40.getType())) {
                            long var41 = ElementCollection.getIndex(var40.voidPos);
                            var7.inventoryFilter = (Short2IntOpenHashMap)this.inventoryFilters.get(var41);
                            var7.inventoryFillUpFilters = (Short2IntOpenHashMap)this.inventoryFillUpFilters.get(var41);
                            var7.inventoryProduction = this.inventoryProduction.get(var41);
                            var7.inventoryProductionLimit = this.inventoryProductionLimit.get(var41);
                            var1.build(var2.x + var40.voidPos.x, var2.y + var40.voidPos.y, var2.z + var40.voidPos.z, var40.getType(), var40.getOrientation(), var40.isActive(), var7, var8, var37, (BuildHelper)null, var3);
                            var1.buildInSymmetry(var2.x + var40.voidPos.x, var2.y + var40.voidPos.y, var2.z + var40.voidPos.z, var40.getType(), var40.getOrientation(), var40.isActive(), var7, var8, var37, var3, (BuildHelper)null, var4, var1);
                        }
                    }

                    long var39 = System.currentTimeMillis() - var9;
                    System.err.println("[COPYAREA] Build done in " + var39 + " ms. Now connecting necessary blocks");
                    Long2ObjectOpenHashMap var42 = new Long2ObjectOpenHashMap();
                    boolean var19 = false;
                    var42.putAll(this.connections);
                    Vector3i var27 = new Vector3i();
                    var5 = 0;

                    Entry var32;
                    for(Iterator var30 = var1.getControlElementMap().getControllingMap().getAll().entrySet().iterator(); var30.hasNext(); var5 += ((FastCopyLongOpenHashSet)var32.getValue()).size()) {
                        var32 = (Entry)var30.next();
                    }

                    LongOpenHashSet var31 = new LongOpenHashSet(var5);
                    Iterator var33 = var1.getControlElementMap().getControllingMap().getAll().entrySet().iterator();

                    while(var33.hasNext()) {
                        Iterator var20 = ((FastCopyLongOpenHashSet)((Entry)var33.next()).getValue()).iterator();

                        while(var20.hasNext()) {
                            long var22 = ElementCollection.getPosIndexFrom4((Long)var20.next());
                            var31.add(var22);
                        }
                    }

                    var33 = this.pieces.iterator();

                    while(true) {
                        VoidSegmentPiece var29;
                        long var43;
                        Iterator var44;
                        do {
                            do {
                                if (!var33.hasNext()) {
                                    if (var19) {
                                        String var34 = Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_7;
                                        if (var42.isEmpty()) {
                                            var34 = Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_8;
                                        }

                                        ((GameClientState)var1.getState()).getController().popupGameTextMessage(StringTools.format(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_9, new Object[]{var34}), 0.0F);
                                    }

                                    var33 = this.pieces.iterator();

                                    label158:
                                    while(true) {
                                        do {
                                            do {
                                                if (!var33.hasNext()) {
                                                    var1.getBlockProcessor().connectionsToAddFromPaste.putAll(var42);
                                                    var33 = this.pieces.iterator();

                                                    while(var33.hasNext()) {
                                                        var29 = (VoidSegmentPiece)var33.next();
                                                        if (this.ok(var1, var29)) {
                                                            var43 = ElementCollection.getIndex(var29.voidPos);
                                                            if (this.textMap.containsKey(var43)) {
                                                                var1.getBlockProcessor().textToAddFromPaste.put(ElementCollection.getIndex(var2.x + var29.voidPos.x, var2.y + var29.voidPos.y, var2.z + var29.voidPos.z), this.textMap.get(var43));
                                                                (var27 = new Vector3i(var29.voidPos)).add(var2);
                                                                this.addSymmetryText(var27, (String)this.textMap.get(var43), var4, var1.getBlockProcessor().textToAddFromPaste);
                                                            }
                                                        }
                                                    }

                                                    return;
                                                }

                                                var29 = (VoidSegmentPiece)var33.next();
                                            } while(!this.ok(var1, var29));

                                            var43 = ElementCollection.getIndex(var29.voidPos);
                                        } while(!var42.containsKey(var43));

                                        var44 = ((LongArrayList)var42.get(var43)).iterator();

                                        while(true) {
                                            while(true) {
                                                if (!var44.hasNext()) {
                                                    continue label158;
                                                }

                                                (var8 = ElementCollection.getPosFromIndex((Long)var44.next(), new Vector3i())).add(var2);
                                                Vector3i var45;
                                                (var45 = new Vector3i(var29.voidPos)).add(var2);
                                                SegmentPiece var25;
                                                if ((var25 = var1.getSegmentBuffer().getPointUnsave(var8)) == null) {
                                                    ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_10, 0.0F);
                                                } else {
                                                    boolean var28 = var25.getType() == 6 || var25.getType() == 38 || var25.getType() == 414 || var25.getType() == 416 || var25.getType() == 4 || var25.getType() == 39 || var25.getType() == 46 || var25.getType() == 54 || var25.getType() == 334 || var25.getType() == 332 || var25.getType() == 344;
                                                    System.err.println("[COPYAREA] Connecting " + var8 + " to " + var29.voidPos + ";  Controllerpiece: " + var25);
                                                    this.addControlConnection(ElementCollection.getIndex(var8), ElementCollection.getIndex(var45), var42, !var28);
                                                    this.connectSymetry(var8, var45, var29.getType(), var4, var42, !var28);
                                                }
                                            }
                                        }
                                    }
                                }

                                var29 = (VoidSegmentPiece)var33.next();
                            } while(!this.ok(var1, var29));

                            var43 = ElementCollection.getIndex(var29.voidPos);
                        } while(!this.connections.containsKey(var43));

                        var44 = ((LongArrayList)this.connections.get(var43)).iterator();

                        while(var44.hasNext()) {
                            var44.next();
                            var27.add(var29.voidPos, var2);
                            long var24 = ElementCollection.getIndex(var27);
                            if (var31.contains(var24)) {
                                var42.remove(var43);
                                var19 = true;
                            }
                        }
                    }
                }
            } else {
                System.err.println("[CLIENT][WARNING] PASTE DENIED BY ACTUAL BLOCKS PLACED. POSSIBLY MODIFIED TEMPLATE TRIED TO PLACE: size from original: " + var6 + "; Read size from blocks: " + var35 + ";              BB " + var12 + "; " + var13);
                ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_11, 0.0F);
            }
        } else {
            ((GameClientState)var1.getState()).getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_COPYAREA_5, 0.0F);
        }
    }

    public void addSymmetryText(Vector3i var1, String var2, SymmetryPlane var3, Long2ObjectOpenHashMap<String> var4) {
        int var6;
        long var11;

        switch(var3.getMode()) {
            case XY:
                var6 = (var3.getPlane().z - var1.z << 1) + var3.getExtraDist();
                var11 = ElementCollection.getIndex(var1.x, var1.y, var1.z + var6);
                this.addSymText(var11, var2, var4);
                break;
            case XZ:
                var6 = (var3.getPlane().y - var1.y << 1) + var3.getExtraDist();
                var11 = ElementCollection.getIndex(var1.x, var1.y + var6, var1.z);
                this.addSymText(var11, var2, var4);
                break;
            case YZ:
                var6 = (var3.getPlane().x - var1.x << 1) + var3.getExtraDist();
                var11 = ElementCollection.getIndex(var1.x + var6, var1.y, var1.z);
                this.addSymText(var11, var2, var4);
                break;
        }
    }

    public void connectSymetry(Vector3i var1, Vector3i var2, short var3, SymmetryPlane var4, Long2ObjectOpenHashMap<LongArrayList> var5, boolean var6) {
        int var7;
        long var9;
        int var11;
        int var16;
        long var18;
        switch(var4.getMode()) {
            case XY:
                var7 = ((var16 = var4.getPlane().z) - var1.z << 1) + var4.getExtraDist();
                var11 = (var16 - var2.z << 1) + var4.getExtraDist();
                var18 = ElementCollection.getIndex(var1.x, var1.y, var1.z + var7);
                var9 = ElementCollection.getIndex(var2.x, var2.y, var2.z + var11);
                this.addControlConnection(var18, var9, var5, var6);
                break;
            case XZ:
                var7 = ((var16 = var4.getPlane().y) - var1.y << 1) + var4.getExtraDist();
                var11 = (var16 - var2.y << 1) + var4.getExtraDist();
                var18 = ElementCollection.getIndex(var1.x, var1.y + var7, var1.z);
                var9 = ElementCollection.getIndex(var2.x, var2.y + var11, var2.z);
                this.addControlConnection(var18, var9, var5, var6);
                break;
            case YZ:
                var7 = ((var16 = var4.getPlane().x) - var1.x << 1) + var4.getExtraDist();
                var11 = (var16 - var2.x << 1) + var4.getExtraDist();
                var18 = ElementCollection.getIndex(var1.x + var7, var1.y, var1.z);
                var9 = ElementCollection.getIndex(var2.x + var11, var2.y, var2.z);
                this.addControlConnection(var18, var9, var5, var6);
                break;
        }
    }

    private void addSymText(long var1, String var3, Long2ObjectOpenHashMap<String> var4) {
        var4.put(var1, var3);
    }

    private void addControlConnection(long var1, long var3, Long2ObjectOpenHashMap<LongArrayList> var5, boolean var6) {
        if (var6 || !var5.containsKey(var3)) {
            LongArrayList var7;
            if ((var7 = (LongArrayList)var5.get(var3)) == null) {
                var7 = new LongArrayList();
                var5.put(var3, var7);
            }

            var7.add(var1);
        }
    }

    public void save(String var1) throws IOException {
        (new FileExt("./templates/")).mkdirs();
        var1 = "./templates/" + var1 + ".smtpl";
        FileExt var8 = new FileExt(var1);
        DataOutputStream var2 = null;
        boolean var6 = false;

        try {
            var6 = true;
            (var2 = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(var8)))).writeByte(5);
            var2.writeInt(this.min.x);
            var2.writeInt(this.min.y);
            var2.writeInt(this.min.z);
            var2.writeInt(this.max.x);
            var2.writeInt(this.max.y);
            var2.writeInt(this.max.z);
            var2.writeInt(this.pieces.size());

            for(int var9 = 0; var9 < this.pieces.size(); ++var9) {
                ((VoidSegmentPiece)this.pieces.get(var9)).serialize(var2);
            }

            var2.writeInt(this.connections.size());
            Iterator var10 = this.connections.entrySet().iterator();

            Entry var3;
            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeInt(((LongArrayList)var3.getValue()).size());

                for(int var4 = 0; var4 < ((LongArrayList)var3.getValue()).size(); ++var4) {
                    var2.writeLong(((LongArrayList)var3.getValue()).getLong(var4));
                }
            }

            var2.writeInt(this.textMap.size());
            var10 = this.textMap.entrySet().iterator();

            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeUTF((String)var3.getValue());
            }

            var2.writeInt(this.inventoryFilters.size());
            var10 = this.inventoryFilters.entrySet().iterator();

            Iterator var11;
            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeInt(((Short2IntOpenHashMap)var3.getValue()).size());
                var11 = ((Short2IntOpenHashMap)var3.getValue()).entrySet().iterator();

                while(var11.hasNext()) {
                    var3 = (Entry)var11.next();
                    var2.writeShort((Short)var3.getKey());
                    var2.writeInt((Integer)var3.getValue());
                }
            }

            var2.writeInt(this.inventoryProduction.size());
            var10 = this.inventoryProduction.entrySet().iterator();

            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeShort((Short)var3.getValue());
            }

            var2.writeInt(this.inventoryProductionLimit.size());
            var10 = this.inventoryProductionLimit.entrySet().iterator();

            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeInt((Integer)var3.getValue());
            }

            var2.writeInt(this.inventoryFillUpFilters.size());
            var10 = this.inventoryFillUpFilters.entrySet().iterator();

            while(var10.hasNext()) {
                var3 = (Entry)var10.next();
                var2.writeLong((Long)var3.getKey());
                var2.writeInt(((Short2IntOpenHashMap)var3.getValue()).size());
                var11 = ((Short2IntOpenHashMap)var3.getValue()).entrySet().iterator();

                while(var11.hasNext()) {
                    var3 = (Entry)var11.next();
                    var2.writeShort((Short)var3.getKey());
                    var2.writeInt((Integer)var3.getValue());
                }
            }

            var6 = false;
        } finally {
            if (var6) {
                if (var2 != null) {
                    var2.close();
                }

            }
        }

        var2.close();
    }

    public void load(String var1) throws IOException {
        FileExt var2 = new FileExt("./templates/" + var1 + ".smtpl");
        this.load((File)var2);
    }

    public void load(File var1) throws IOException {
        DataInputStream var2 = null;
        boolean var4 = false;

        try {
            var4 = true;
            byte var6;
            if ((var6 = (var2 = new DataInputStream(new BufferedInputStream(new FileInputStream(var1)))).readByte()) == 1) {
                this.loadVersion1(var2);
                var4 = false;
            } else if (var6 == 2) {
                this.loadVersion2(var2);
                var4 = false;
            } else if (var6 == 3) {
                this.loadVersion3(var2);
                var4 = false;
            } else if (var6 == 4) {
                this.loadVersion4(var2);
                var4 = false;
            } else {
                if (var6 != 5) {
                    throw new IOException("Unknown Template Version " + var6);
                }

                this.loadVersion5(var2);
                var4 = false;
            }
        } finally {
            if (var4) {
                if (var2 != null) {
                    var2.close();
                }

            }
        }

        var2.close();
    }

    private void loadVersion5(DataInputStream var1) throws IOException {
        this.loadVersion4(var1);
        int var2 = var1.readInt();

        int var3;
        for(var3 = 0; var3 < var2; ++var3) {
            long var4 = var1.readLong();
            int var6 = var1.readInt();
            this.inventoryProductionLimit.put(var4, var6);
        }

        var3 = var1.readInt();

        for(int var9 = 0; var9 < var3; ++var9) {
            long var5 = var1.readLong();
            var2 = var1.readInt();
            Short2IntOpenHashMap var7 = new Short2IntOpenHashMap(var2);

            for(int var8 = 0; var8 < var2; ++var8) {
                var7.put(var1.readShort(), var1.readInt());
            }

            this.inventoryFillUpFilters.put(var5, var7);
        }

    }

    private void loadVersion4(DataInputStream var1) throws IOException {
        this.min = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        this.max = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        int var2 = var1.readInt();

        int var3;
        for(var3 = 0; var3 < var2; ++var3) {
            VoidSegmentPiece var4;
            (var4 = new VoidSegmentPiece()).deserialize(var1);
            this.pieces.add(var4);
        }

        var3 = var1.readInt();

        int var7;
        int var9;
        int var12;
        for(var12 = 0; var12 < var3; ++var12) {
            long var5 = var1.readLong();
            var7 = var1.readInt();
            LongArrayList var8 = new LongArrayList(var7);

            for(var9 = 0; var9 < var7; ++var9) {
                var8.add(var1.readLong());
            }

            this.connections.put(var5, var8);
        }

        var12 = var1.readInt();

        int var13;
        for(var13 = 0; var13 < var12; ++var13) {
            long var6 = var1.readLong();
            String var16 = var1.readUTF();
            this.textMap.put(var6, var16);
        }

        var13 = var1.readInt();

        int var14;
        for(var14 = 0; var14 < var13; ++var14) {
            long var15 = var1.readLong();
            var9 = var1.readInt();
            Short2IntOpenHashMap var10 = new Short2IntOpenHashMap(var9);

            for(var3 = 0; var3 < var9; ++var3) {
                var10.put(var1.readShort(), var1.readInt());
            }

            this.inventoryFilters.put(var15, var10);
        }

        var14 = var1.readInt();

        for(var7 = 0; var7 < var14; ++var7) {
            long var17 = var1.readLong();
            short var11 = var1.readShort();
            this.inventoryProduction.put(var17, var11);
        }

    }

    private void loadVersion3(DataInputStream var1) throws IOException {
        this.min = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        this.max = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        int var2 = var1.readInt();

        int var3;
        for(var3 = 0; var3 < var2; ++var3) {
            VoidSegmentPiece var4;
            (var4 = new VoidSegmentPiece()).voidPos.set(var1.readInt(), var1.readInt(), var1.readInt());
            SegmentData3Byte.migrateTo(var1.readByte(), var1.readByte(), var1.readByte(), var4);
            this.pieces.add(var4);
        }

        var3 = var1.readInt();

        int var7;
        int var9;
        int var12;
        for(var12 = 0; var12 < var3; ++var12) {
            long var5 = var1.readLong();
            var7 = var1.readInt();
            LongArrayList var8 = new LongArrayList(var7);

            for(var9 = 0; var9 < var7; ++var9) {
                var8.add(var1.readLong());
            }

            this.connections.put(var5, var8);
        }

        var12 = var1.readInt();

        int var13;
        for(var13 = 0; var13 < var12; ++var13) {
            long var6 = var1.readLong();
            String var16 = var1.readUTF();
            this.textMap.put(var6, var16);
        }

        var13 = var1.readInt();

        int var14;
        for(var14 = 0; var14 < var13; ++var14) {
            long var15 = var1.readLong();
            var9 = var1.readInt();
            Short2IntOpenHashMap var10 = new Short2IntOpenHashMap(var9);

            for(var3 = 0; var3 < var9; ++var3) {
                var10.put(var1.readShort(), var1.readInt());
            }

            this.inventoryFilters.put(var15, var10);
        }

        var14 = var1.readInt();

        for(var7 = 0; var7 < var14; ++var7) {
            long var17 = var1.readLong();
            short var11 = var1.readShort();
            this.inventoryProduction.put(var17, var11);
        }

    }

    private void loadVersion2(DataInputStream var1) throws IOException {
        this.min = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        this.max = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        int var2 = var1.readInt();

        int var3;
        for(var3 = 0; var3 < var2; ++var3) {
            VoidSegmentPiece var4;
            (var4 = new VoidSegmentPiece()).voidPos.set(var1.readInt(), var1.readInt(), var1.readInt());
            SegmentData3Byte.migrateTo(var1.readByte(), var1.readByte(), var1.readByte(), var4);
            this.pieces.add(var4);
        }

        var3 = var1.readInt();

        int var11;
        for(var11 = 0; var11 < var3; ++var11) {
            long var5 = var1.readLong();
            int var7 = var1.readInt();
            LongArrayList var9 = new LongArrayList(var7);

            for(int var8 = 0; var8 < var7; ++var8) {
                var9.add(var1.readLong());
            }

            this.connections.put(var5, var9);
        }

        var11 = var1.readInt();

        for(int var12 = 0; var12 < var11; ++var12) {
            long var6 = var1.readLong();
            String var10 = var1.readUTF();
            this.textMap.put(var6, var10);
        }

    }

    private void loadVersion1(DataInputStream var1) throws IOException {
        this.min = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        this.max = new Vector3i(var1.readInt(), var1.readInt(), var1.readInt());
        int var2 = var1.readInt();

        int var3;
        for(var3 = 0; var3 < var2; ++var3) {
            VoidSegmentPiece var4;
            (var4 = new VoidSegmentPiece()).voidPos.set(var1.readInt(), var1.readInt(), var1.readInt());
            SegmentData3Byte.migrateTo(var1.readByte(), var1.readByte(), var1.readByte(), var4);
            this.pieces.add(var4);
        }

        var3 = var1.readInt();

        for(int var9 = 0; var9 < var3; ++var9) {
            long var5 = var1.readLong();
            var2 = var1.readInt();
            LongArrayList var7 = new LongArrayList(var2);

            for(int var8 = 0; var8 < var2; ++var8) {
                var7.add(var1.readLong());
            }

            this.connections.put(var5, var7);
        }

    }

    class BuildCB implements BuildCallback {
        private Short2IntOpenHashMap inventoryFilter = null;
        private short inventoryProduction = 0;
        private final SegmentController segmentController;
        private final LongOpenHashSet built = new LongOpenHashSet();
        public Short2IntOpenHashMap inventoryFillUpFilters = null;
        public int inventoryProductionLimit;

        public BuildCB(SegmentController var2) {
            this.segmentController = var2;
        }

        public long getSelectedControllerPos() {
            return -9223372036854775808L;
        }

        public void onBuild(Vector3i var1, Vector3i var2, short var3) {
            var2 = null;
            if (this.segmentController instanceof ManagedSegmentController) {
                ManagerContainer var4 = ((ManagedSegmentController)this.segmentController).getManagerContainer();
                if (this.inventoryFilter != null || this.inventoryProduction != 0 || this.inventoryFillUpFilters != null || this.inventoryProductionLimit != 0) {
                    var4.addDelayedProductionAndFilterClientSet(var1, this.inventoryFilter, this.inventoryFillUpFilters, this.inventoryProduction, this.inventoryProductionLimit);
                }
            }

            this.built.add(ElementCollection.getIndex(var1));
        }
    }
}