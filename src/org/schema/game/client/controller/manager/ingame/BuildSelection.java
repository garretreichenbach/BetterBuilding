package org.schema.game.client.controller.manager.ingame;

import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Iterator;
import javax.vecmath.Vector3f;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.SelectionShader;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.Mesh;
import org.schema.schine.graphicsengine.shader.ShaderLibrary;

/**
 * BuildSelection.java
 * ==================================================
 * Modified 01/27/2021 by TheDerpGamer
 * @author Schema
 */
public abstract class BuildSelection {
    public Vector3i selectionBoxA = null;
    public Vector3i selectionBoxB = null;
    public float cameraDistance = 3.0F;

    public BuildSelection() {
    }

    public void setSelectionBoxOrigin(Vector3i var1) {
        this.selectionBoxA = var1;
    }

    public void resetSelectionBox() {
        this.selectionBoxA = null;
        this.selectionBoxB = null;
    }

    protected abstract boolean canExecute(PlayerInteractionControlManager var1);

    public void handleMouseEvent(PlayerInteractionControlManager var1, MouseEvent var2) {
        BuildToolsManager var3 = var1.getBuildToolsManager();
        SegmentControlManager var4 = var1.getSegmentControlManager();
        Vector3f var5 = new Vector3f(Controller.getCamera().getViewable().getPos());
        Vector3f var6;
        (var6 = Controller.getCamera().getForward(new Vector3f())).scale(this.cameraDistance);
        var5.add(var6);
        var4.getSegmentController().getWorldTransformInverse().transform(var5);
        var5.x = (float)(Math.round(var5.x) + 16);
        var5.y = (float)(Math.round(var5.y) + 16);
        var5.z = (float)(Math.round(var5.z) + 16);
        if (var2.pressedLeftMouse()) {
            if (this.selectionBoxA == null) {
                this.setSelectionBoxOrigin(new Vector3i(var5));
                System.out.println("COPY Setting boxA " + var5);
                if (this.isSingleSelect()) {
                    this.callback(var1, var2);
                    this.resetSelectionBox();
                    var3.selectionPlaced = true;
                    var3.setSelectMode((BuildSelection)null);
                    var4.getSegmentBuildController().setPlaceMode(false);
                    var1.getPlayerCharacterManager().setPlaceMode(false);
                    var1.getInShipControlManager().getShipControlManager().getSegmentBuildController().setPlaceMode(false);
                }

                return;
            }

            if (this.canExecute(var1)) {
                this.selectionBoxB = new Vector3i(var5);
                System.out.println("COPY Setting boxB " + var5);
                this.callback(var1, var2);
                this.resetSelectionBox();
                var3.setSelectMode((BuildSelection)null);
                var4.getSegmentBuildController().setPlaceMode(false);
                var1.getPlayerCharacterManager().setPlaceMode(false);
                var3.selectionPlaced = true;
                var1.getInShipControlManager().getShipControlManager().getSegmentBuildController().setPlaceMode(false);
                System.out.println("COPY handling event 1");
            } else {
                this.selectionBoxB = new Vector3i(var5);
                Vector3i var7;
                (var7 = new Vector3i(this.selectionBoxB)).sub(this.selectionBoxA);
                var7.x = (int)((float)var7.x + Math.signum((float)var7.x));
                var7.y = (int)((float)var7.y + Math.signum((float)var7.y));
                var7.z = (int)((float)var7.z + Math.signum((float)var7.z));
                var7.absolute();
                var3.width.set((float)var7.x);
                var3.height.set((float)var7.y);
                var3.depth.set((float)var7.z);
                this.resetSelectionBox();
                var3.setSelectMode((BuildSelection)null);
                var3.selectionPlaced = true;
                System.out.println("COPY handling event 2");
            }
        }

        if (var2.pressedRightMouse()) {
            this.resetSelectionBox();
        }

    }

    protected abstract boolean isSingleSelect();

    protected abstract void callback(PlayerInteractionControlManager var1, MouseEvent var2);

    protected abstract BuildSelection.DrawStyle getDrawStyle();

    public void draw(GameClientState var1, SegmentController var2, Mesh var3, SelectionShader var4) {
        switch(this.getDrawStyle()) {
            case BOX:
                this.drawBox(var1, var2, var3, var4);
                return;
            case LINE:
                this.drawLine(var1, var2, var3, var4);
                return;
            default:
                throw new RuntimeException("Unknown Draw Style " + this.getDrawStyle());
        }
    }

    private void drawLine(GameClientState var1, SegmentController var2, Mesh var3, SelectionShader var4) {
        GlUtil.glEnable(3042);
        GlUtil.glDisable(2896);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        Vector3f var12 = new Vector3f(1.0F, 1.0F, 1.0F);
        Vector3f var5;
        (var5 = Controller.getCamera().getForward(new Vector3f())).scale(this.cameraDistance);
        Transform var6 = new Transform(var2.getWorldTransform());
        ObjectOpenHashSet var7 = new ObjectOpenHashSet();
        Vector3f var8;
        Vector3f var9;
        if (this.selectionBoxA != null) {
            Vector3f var10000 = var8 = new Vector3f((float)this.selectionBoxA.x, (float)this.selectionBoxA.y, (float)this.selectionBoxA.z);
            var10000.x -= 16.0F;
            var8.y -= 16.0F;
            var8.z -= 16.0F;
            (var9 = new Vector3f(Controller.getCamera().getViewable().getPos())).add(var5);
            var2.getWorldTransformInverse().transform(var9);
            var9.x = (float)Math.round(var9.x);
            var9.y = (float)Math.round(var9.y);
            var9.z = (float)Math.round(var9.z);
            (var5 = new Vector3f()).sub(var9, var8);
            float var14 = Math.max(0.5F, var5.length());
            var5.normalize();

            for(float var10 = 0.0F; var10 < var14; var10 += 0.5F) {
                Vector3f var11;
                var10000 = var11 = new Vector3f(var8);
                var10000.x += (float)Math.round(var10 * var5.x);
                var11.y += (float)Math.round(var10 * var5.y);
                var11.z += (float)Math.round(var10 * var5.z);
                var7.add(var11);
            }
        } else {
            var6 = new Transform(var2.getWorldTransform());
            (var8 = new Vector3f(Controller.getCamera().getViewable().getPos())).add(var5);
            var2.getWorldTransformInverse().transform(var8);
            var8.x = (float)Math.round(var8.x);
            var8.y = (float)Math.round(var8.y);
            var8.z = (float)Math.round(var8.z);
            var7.add(var8);
        }

        var12.add(new Vector3f(0.01F, 0.01F, 0.01F));
        Iterator var13 = var7.iterator();

        while(var13.hasNext()) {
            var9 = (Vector3f)var13.next();
            var6.set(var2.getWorldTransform());
            var6.basis.transform(var9);
            var6.origin.add(var9);
            GlUtil.glPushMatrix();
            GlUtil.glMultMatrix(var6);
            GlUtil.scaleModelview(var12.x, var12.y, var12.z);
            ShaderLibrary.selectionShader.setShaderInterface(var4);
            ShaderLibrary.selectionShader.load();
            GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.1F, 0.9F, 0.6F, 0.65F);
            var3.renderVBO();
            GlUtil.glPopMatrix();
        }

        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlUtil.glEnable(2896);
        GlUtil.glDisable(3042);
    }

    private void drawBox(GameClientState var1, SegmentController var2, Mesh var3, SelectionShader var4) {
        GlUtil.glEnable(3042);
        GlUtil.glDisable(2896);
        GlUtil.glBlendFunc(770, 771);
        GlUtil.glBlendFuncSeparate(770, 771, 1, 771);
        Vector3f var5 = new Vector3f(1.0F, 1.0F, 1.0F);
        Vector3f var6;
        (var6 = Controller.getCamera().getForward(new Vector3f())).scale(this.cameraDistance);
        Transform var7;
        Vector3f var8;
        if (this.selectionBoxA != null) {
            var7 = new Transform(var2.getWorldTransform());
            var8 = new Vector3f((float)this.selectionBoxA.x, (float)this.selectionBoxA.y, (float)this.selectionBoxA.z);
            (var5 = new Vector3f(Controller.getCamera().getViewable().getPos())).add(var6);
            var8.x = (float)Math.round(var8.x);
            var8.y = (float)Math.round(var8.y);
            var8.z = (float)Math.round(var8.z);
            var2.getWorldTransformInverse().transform(var5);
            var5.x = (float)(Math.round(var5.x) + 16);
            var5.y = (float)(Math.round(var5.y) + 16);
            var5.z = (float)(Math.round(var5.z) + 16);
            var5.sub(var8);
            var8.x -= 16.0F;
            var8.y -= 16.0F;
            var8.z -= 16.0F;
            var5.x += Math.signum(var5.x);
            var5.y += Math.signum(var5.y);
            var5.z += Math.signum(var5.z);
            float var9 = var1.getMaxBuildArea();
            var5.x = Math.signum(var5.x) > 0.0F ? Math.min(var5.x, Math.signum(var5.x) * var9) : Math.max(var5.x, Math.signum(var5.x) * var9);
            var5.y = Math.signum(var5.y) > 0.0F ? Math.min(var5.y, Math.signum(var5.y) * var9) : Math.max(var5.y, Math.signum(var5.y) * var9);
            var5.z = Math.signum(var5.z) > 0.0F ? Math.min(var5.z, Math.signum(var5.z) * var9) : Math.max(var5.z, Math.signum(var5.z) * var9);
            var8.x += var5.x / 2.0F - 0.5F * Math.signum(var5.x);
            var8.y += var5.y / 2.0F - 0.5F * Math.signum(var5.y);
            var8.z += var5.z / 2.0F - 0.5F * Math.signum(var5.z);
            if (var5.x == 0.0F) {
                var5.x = 1.0F;
            }

            if (var5.y == 0.0F) {
                var5.y = 1.0F;
            }

            if (var5.z == 0.0F) {
                var5.z = 1.0F;
            }

            var7.basis.transform(var8);
            var7.origin.add(var8);
        } else {
            var7 = new Transform(var2.getWorldTransform());
            (var8 = new Vector3f(Controller.getCamera().getViewable().getPos())).add(var6);
            var2.getWorldTransformInverse().transform(var8);
            var8.x = (float)Math.round(var8.x);
            var8.y = (float)Math.round(var8.y);
            var8.z = (float)Math.round(var8.z);
            var7.basis.transform(var8);
            var7.origin.add(var8);
        }

        GlUtil.glPushMatrix();
        GlUtil.glMultMatrix(var7);
        var5.add(new Vector3f(Math.signum(var5.x) * 0.02F, Math.signum(var5.y) * 0.02F, Math.signum(var5.z) * 0.02F));
        GlUtil.scaleModelview(var5.x, var5.y, var5.z);
        ShaderLibrary.selectionShader.setShaderInterface(var4);
        ShaderLibrary.selectionShader.load();
        GlUtil.updateShaderVector4f(ShaderLibrary.selectionShader, "selectionColor", 0.1F, 0.9F, 0.6F, 0.65F);
        var3.renderVBO();
        GlUtil.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlUtil.glEnable(2896);
        GlUtil.glDisable(3042);
        GlUtil.glPopMatrix();
    }

    public static enum DrawStyle {
        BOX,
        LINE;

        private DrawStyle() {
        }
    }
}