package net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry;

import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.cubes.shapes.BlockShapeAlgorithm;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.world.SegmentData;

/**
 * SymmetryPlane.java
 * Improved and more versatile version of SymmetryPlanes
 * ==================================================
 * Created 01/28/2021
 * @author TheDerpGamer
 */
public class SymmetryPlane {

    private SymmetryMode mode;
    private Vector3i plane;
    private boolean placeMode;
    private boolean enabled;
    private boolean mirrorCubeShapes;
    private boolean mirrorNonCubicShapes;
    private int extraDist;

    public SymmetryPlane(SymmetryMode mode) {
        this.mode = mode;
        this.plane = new Vector3i(SegmentData.SEG_HALF, SegmentData.SEG_HALF, SegmentData.SEG_HALF);
        this.placeMode = false;
        this.enabled = false;
        this.mirrorCubeShapes = true;
        this.mirrorNonCubicShapes = true;
        this.extraDist = 0;
    }

    public int getMirrorOrientation(short type, int elementOrientation) {
        if(type != Element.TYPE_NONE) {
            ElementInformation info = ElementKeyMap.getInfo(type);
            if(info.getBlockStyle() != BlockStyle.NORMAL) {
                if(mirrorNonCubicShapes) {
                    switch(mode) {
                        case XY:
                            elementOrientation = BlockShapeAlgorithm.xyMappings[info.blockStyle.id - 1][elementOrientation%24];
                            break;
                        case XZ:
                            elementOrientation = BlockShapeAlgorithm.xzMappings[info.blockStyle.id - 1][elementOrientation%24];
                            break;
                        case YZ:
                            elementOrientation = BlockShapeAlgorithm.yzMappings[info.blockStyle.id - 1][elementOrientation%24];
                            break;
                    }
                }
            } else {
                if(mirrorCubeShapes && type != ElementKeyMap.CARGO_SPACE && (ElementKeyMap.getInfo(type).getIndividualSides() > 0 || ElementKeyMap.getInfo(type).isOrientatable())) {
                    if(mode.equals(SymmetryMode.XY) && (elementOrientation == Element.FRONT || elementOrientation == Element.BACK)) {
                        elementOrientation = Element.getOpposite(elementOrientation);
                    }
                    if(mode.equals(SymmetryMode.XZ) && (elementOrientation == Element.TOP || elementOrientation == Element.BOTTOM)) {
                        elementOrientation = Element.getOpposite(elementOrientation);
                    }
                    if(mode.equals(SymmetryMode.YZ) && (elementOrientation == Element.RIGHT || elementOrientation == Element.LEFT)) {
                        elementOrientation = Element.getOpposite(elementOrientation);
                    }
                }
                assert (elementOrientation <= 6);
            }
        }

        return elementOrientation;
    }

    public SymmetryMode getMode() {
        return mode;
    }

    public void setMode(SymmetryMode mode) {
        this.mode = mode;
    }

    public Vector3i getPlane() {
        return plane;
    }

    public boolean inPlaceMode() {
        return placeMode;
    }

    public void setPlaceMode(boolean placeMode) {
        this.placeMode = placeMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMirrorCubeShapes() {
        return mirrorCubeShapes;
    }

    public void setMirrorCubeShapes(boolean mirrorCubeShapes) {
        this.mirrorCubeShapes = mirrorCubeShapes;
    }

    public boolean isMirrorNonCubicShapes() {
        return mirrorNonCubicShapes;
    }

    public void setMirrorNonCubicShapes(boolean mirrorNonCubicShapes) {
        this.mirrorNonCubicShapes = mirrorNonCubicShapes;
    }

    public int getExtraDist() {
        return extraDist;
    }

    public void setExtraDist(int extraDist) {
        this.extraDist = extraDist;
    }
}