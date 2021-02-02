package net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry;

/**
 * SymmetryMode.java
 * <Description>
 * ==================================================
 * Created 02/02/2021
 * @author TheDerpGamer
 */
public enum SymmetryMode {
    NONE(0),
    XY(1),
    XZ(2),
    YZ(4),
    COPY(8),
    PASTE(16),
    PLACE(32);

    public int id;

    SymmetryMode(int id) {
        this.id = id;
    }

    public static SymmetryMode getFromId(int i) {
        for(SymmetryMode mode : values()) {
            if(mode.id == i) return  mode;
        }
        return NONE;
    }
}
