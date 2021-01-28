package net.thederpgamer.betterbuilding.util;

/**
 * GUIScale.java
 * GUI scaling utils
 * ==================================================
 * Created 01/22/2021
 * @author TheDerpGamer
 */
public enum GUIScale {

    S_100("Scale100"),
    S_150("Scale150"),
    S_200("Scale200");

    //Settings
    public static GUIScale S = GUIScale.S_100;
    public int defaultHeight = 24;
    public int defaultInset = 4;

    public final String name;

    GUIScale(String name) {
        this.name = name;
    }

    public int scale(int i) {
        switch(this) {
            case S_100:
                return i;
            case S_150:
                return i+i/2;
            case S_200:
                return i+i;
        }
        throw new RuntimeException("Invalid Scale "+this);
    }
}
