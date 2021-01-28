package org.schema.game.client.controller.manager.ingame;

import com.bulletphysics.util.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.AbstractControlManager;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.buildhelper.BuildHelper;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;

/**
 * BuildToolsManager.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public class BuildToolsManager extends AbstractControlManager implements GUICallback {
    private final ObjectArrayList<Class<? extends BuildHelper>> buildHelperClasses = new ObjectArrayList();
    public String user = "";
    public Object2ObjectOpenHashMap<String, SizeSetting[]> savedMap = new Object2ObjectOpenHashMap();
    public final SizeSetting width;
    public final SizeSetting height;
    public final SizeSetting depth;
    public final SizeSetting orientation;
    public final FillSetting fill;

    private final ArrayList<SymmetryPlane> xyPlanes = new ArrayList<>();
    private final ArrayList<SymmetryPlane> xzPlanes = new ArrayList<>();
    private final ArrayList<SymmetryPlane> yzPlanes = new ArrayList<>();

    public boolean add = true;
    public boolean lighten = false;
    public boolean buildHelperReplace;
    public boolean showCenterOfMass;
    public boolean buildInfo;
    public boolean structureInfo = true;
    private BuildSelection selectMode;
    private boolean copyMode;
    private boolean pasteMode;
    private CopyArea copyArea;
    private short removeFilter;
    private boolean replaceRemoveFilter;
    private BuildHelper buildHelper;
    private BuildToolCreateDocking buildToolCreateDocking;
    public final SlabSetting slabSize;
    public boolean selectionPlaced;
    private FillTool fillTool;
    public boolean reactorHull;

    public BuildToolsManager(GameClientState var1) {
        super(var1);
        this.width = new SizeSetting(var1);
        this.height = new SizeSetting(var1);
        this.depth = new SizeSetting(var1);
        this.orientation = new SizeSetting(var1);
        this.slabSize = new SlabSetting(var1);
        this.fill = new FillSetting(var1);
        this.readBuildHelperClasses();
    }

    private void readBuildHelperClasses() {
        try {
            Iterator var1 = (new Reflections("org.schema.game.client.view.buildhelper", new Scanner[0])).getSubTypesOf(BuildHelper.class).iterator();

            while(var1.hasNext()) {
                Class var2 = (Class)var1.next();
                this.buildHelperClasses.add(var2);
            }

            Collections.sort(this.buildHelperClasses, new Comparator<Class<? extends BuildHelper>>() {
                public int compare(Class<? extends BuildHelper> var1, Class<? extends BuildHelper> var2) {
                    return var1.getSimpleName().compareTo(var2.getSimpleName());
                }
            });
        } catch (Exception var3) {
            var3.printStackTrace();
        }
    }

    public void callback(GUIElement var1, MouseEvent var2) {
    }

    public boolean isOccluded() {
        return false;
    }

    public int getWidth() {
        return this.width.setting;
    }

    public int getHeight() {
        return this.height.setting;
    }

    public int getDepth() {
        return this.depth.setting;
    }

    public Vector3i getSize() {
        return this.copyArea != null && this.isPasteMode() ? this.copyArea.getSize() : new Vector3i(this.getWidth(), this.getHeight(), this.getDepth());
    }

    public Vector3f getSizef() {
        return this.copyArea != null && this.isPasteMode() ? this.copyArea.getSizef() : new Vector3f((float)this.getWidth(), (float)this.getHeight(), (float)this.getDepth());
    }

    public void load(String var1) {
        SizeSetting[] var2;
        if ((var2 = (SizeSetting[])this.savedMap.get(var1)) != null) {
            this.width.set((float)var2[0].setting);
            this.height.set((float)var2[1].setting);
            this.depth.set((float)var2[2].setting);
        }

    }

    public void save(String var1) {
        SizeSetting[] var2;
        (var2 = new SizeSetting[3])[0] = new SizeSetting(this.getState());
        var2[1] = new SizeSetting(this.getState());
        var2[2] = new SizeSetting(this.getState());
        var2[0].set((float)this.getWidth());
        var2[1].set((float)this.getHeight());
        var2[2].set((float)this.getDepth());
        this.savedMap.put(var1, var2);
    }

    public void reset() {
        System.err.println("[CLIENT][BUILDTOOLS] Reset area placement");
        this.width.reset();
        this.height.reset();
        this.depth.reset();
        this.copyMode = false;
        this.pasteMode = false;
        this.cancelCreateDockingMode();
    }

    public PlayerInteractionControlManager getInteractionControlManager() {
        return this.getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
    }

    public boolean canUndo() {
        return this.getInteractionControlManager().canUndo();
    }

    public void undo() {
        this.getInteractionControlManager().undo();
    }

    public boolean isUndoRedoOnCooldown() {
        return this.getInteractionControlManager().isUndoRedoOnCooldown();
    }

    public void redo() {
        this.getInteractionControlManager().redo();
    }

    public boolean canRedo() {
        return this.getInteractionControlManager().canRedo();
    }

    public boolean isAddMode() {
        return this.add && !this.isCopyMode() && (this.getBuildHelper() == null || this.getBuildHelper().placed);
    }

    public boolean isCopyMode() {
        return this.copyMode;
    }

    public void setCopyMode(boolean var1) {
        this.copyMode = var1;
    }

    public boolean isPasteMode() {
        return this.pasteMode;
    }

    public void setPasteMode(boolean var1) {
        this.pasteMode = var1;
    }

    public boolean canPaste() {
        return this.copyArea != null && this.copyArea.getPieces().size() > 0;
    }

    public void saveCopyArea(String var1) throws IOException {
        if (this.copyArea != null) {
            this.copyArea.save(var1);
        }

    }

    public void loadCopyArea(String var1) throws IOException {
        this.copyArea = new CopyArea();
        this.copyArea.load(var1);
    }

    public void loadCopyArea(File var1) throws IOException {
        this.copyArea = new CopyArea();
        this.copyArea.load(var1);
    }

    public void setCopyArea2vectors(SegmentController var1, Vector3i var2, Vector3i var3, int var4) {
        if (var4 > 0) {
            --var4;
            var3.x = var2.x > var3.x ? Math.max(var3.x, var2.x - var4) : Math.min(var3.x, var2.x + var4);
            var3.y = var2.y > var3.y ? Math.max(var3.y, var2.y - var4) : Math.min(var3.y, var2.y + var4);
            var3.z = var2.z > var3.z ? Math.max(var3.z, var2.z - var4) : Math.min(var3.z, var2.z + var4);
        }

        var4 = Math.min(var2.x, var3.x);
        int var5 = Math.min(var2.y, var3.y);
        int var6 = Math.min(var2.z, var3.z);
        int var7 = Math.max(var2.x, var3.x);
        int var8 = Math.max(var2.y, var3.y);
        int var9 = Math.max(var2.z, var3.z);
        System.out.println("Copy Area, cornerA " + var2 + " cornerB " + var3);
        this.copyArea = new CopyArea();
        this.copyArea.copyArea(var1, new Vector3i(var4, var5, var6), new Vector3i(var7, var8, var9));
    }

    public void setCopyArea(SegmentController var1, Vector3i var2, Vector3i var3) {
        var3.x = (int)((float)var3.x - Math.signum((float)var3.x));
        var3.y = (int)((float)var3.y - Math.signum((float)var3.y));
        var3.z = (int)((float)var3.z - Math.signum((float)var3.z));
        int var4 = Math.min(var2.x, var2.x + var3.x);
        int var5 = Math.min(var2.y, var2.y + var3.y);
        int var6 = Math.min(var2.z, var2.z + var3.z);
        int var7 = Math.max(var2.x, var2.x + var3.x);
        int var8 = Math.max(var2.y, var2.y + var3.y);
        int var9 = Math.max(var2.z, var2.z + var3.z);
        this.copyArea = new CopyArea();
        this.copyArea.copyArea(var1, new Vector3i(var4, var5, var6), new Vector3i(var7, var8, var9));
    }

    public CopyArea getCopyArea() {
        return this.copyArea;
    }

    public void setCopyArea(CopyArea var1) {
        this.copyArea = var1;
    }

    public boolean isSelectMode() {
        return this.selectMode != null;
    }

    public BuildSelection getSelectMode() {
        return this.selectMode;
    }

    public void setSelectMode(BuildSelection var1) {
        this.selectMode = var1;
    }

    public short getRemoveFilter() {
        return this.removeFilter;
    }

    public void setRemoveFilter(short var1) {
        this.removeFilter = var1;
    }

    public boolean isReplaceRemoveFilter() {
        return this.replaceRemoveFilter;
    }

    public void setReplaceRemoveFilter(boolean var1) {
        this.replaceRemoveFilter = var1;
    }

    public ObjectArrayList<Class<? extends BuildHelper>> getBuildHelperClasses() {
        return this.buildHelperClasses;
    }

    public BuildHelper getBuildHelper() {
        return this.buildHelper;
    }

    public void setBuildHelper(BuildHelper var1) {
        this.buildHelper = var1;
    }

    public boolean isInCreateDockingMode() {
        return this.buildToolCreateDocking != null;
    }

    public void startCreateDockingMode() {
        if (this.getState().getShip() != null && this.getState().getShip().getDockingController().isInAnyDockingRelation()) {
            this.getState().getController().popupAlertTextMessage(Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_BUILDTOOLSMANAGER_0, 0.0F);
        } else {
            this.buildToolCreateDocking = new BuildToolCreateDocking();
        }
    }

    public void cancelCreateDockingMode() {
        this.buildToolCreateDocking = null;
    }

    public String getCreateDockingModeMsg() {
        return !this.isInCreateDockingMode() ? Lng.ORG_SCHEMA_GAME_CLIENT_CONTROLLER_MANAGER_INGAME_BUILDTOOLSMANAGER_1 : this.buildToolCreateDocking.getButtonMsg();
    }

    public BuildToolCreateDocking getBuildToolCreateDocking() {
        return this.buildToolCreateDocking;
    }

    public FillTool getFillTool() {
        return this.fillTool;
    }

    public void setFillTool(FillTool var1) {
        this.fillTool = var1;
    }

    public ArrayList<SymmetryPlane> getXyPlanes() {
        return xyPlanes;
    }

    public ArrayList<SymmetryPlane> getXzPlanes() {
        return xzPlanes;
    }

    public ArrayList<SymmetryPlane> getYzPlanes() {
        return yzPlanes;
    }

    public boolean isCameraDroneDisplayName() {
        return EngineSettings.CAMERA_DRONE_DISPLAY_NAMES.isOn();
    }

    public void setCameraDroneDisplayName(boolean var1) {
        EngineSettings.CAMERA_DRONE_DISPLAY_NAMES.setCurrentState(var1);

        try {
            EngineSettings.write();
        } catch (IOException var2) {
            var2.printStackTrace();
        }
    }
}