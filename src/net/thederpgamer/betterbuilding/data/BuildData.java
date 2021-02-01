package net.thederpgamer.betterbuilding.data;

import net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry.SymmetryPlane;
import java.util.ArrayList;

/**
 * BuildData.java
 * Stores data involving advanced build mode
 * ==================================================
 * Created 01/29/2021
 * @author TheDerpGamer
 */
public class BuildData {

    public static ArrayList<SymmetryPlane> xyPlanes = new ArrayList<>();
    public static ArrayList<SymmetryPlane> xzPlanes = new ArrayList<>();
    public static ArrayList<SymmetryPlane> yzPlanes = new ArrayList<>();

    public static SymmetryPlane currentPlane;

    public static ArrayList<SymmetryPlane> getAllPlanes() {
        ArrayList<SymmetryPlane> symmetryPlanes = new ArrayList<>();
        symmetryPlanes.addAll(xyPlanes);
        symmetryPlanes.addAll(xzPlanes);
        symmetryPlanes.addAll(yzPlanes);
        return symmetryPlanes;
    }
}
