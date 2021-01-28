package net.thederpgamer.betterbuilding.util;

import api.DebugFile;
import net.thederpgamer.betterbuilding.BetterBuilding;
import net.thederpgamer.betterbuilding.inventory.HotbarData;
import java.io.*;

/**
 * HotbarUtils.java
 * Utilities for saving and loading hotbar layouts
 * ==================================================
 * Created 01/27/2021
 * @author TheDerpGamer
 */
public class HotbarUtils {

    private static File hotbarsFile;

    public static void initialize() {
        hotbarsFile = new File(BetterBuilding.getInstance().getResourcesFolder().getPath() + "/hotbars.smdat");
        if(!hotbarsFile.exists()) {
            try {
                hotbarsFile.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    public static void saveHotbars(HotbarData[][] hotbars) {
        try {
            FileOutputStream fileStream = new FileOutputStream(hotbarsFile);
            ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
            for(int h = 0; h < 10; h ++) {
                for(int i = 0; i < 10; i ++) {
                    objectStream.writeObject(hotbars[h][i]);
                }
            }
            objectStream.close();
            fileStream.close();
            DebugFile.log("[INFO]: Successfully saved hotbars", BetterBuilding.getInstance());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HotbarData[][] loadHotbars() {
        HotbarData[][] hotbars = new HotbarData[10][10];
        try {
            FileInputStream fileStream = new FileInputStream(hotbarsFile);
            ObjectInputStream objectStream = new ObjectInputStream(fileStream);
            for(int h = 0; h < 10; h ++) {
                for(int i = 0; i < 10; i ++) {
                    hotbars[h][i] = (HotbarData) objectStream.readObject();
                }
            }
            objectStream.close();
            fileStream.close();
            DebugFile.log("[INFO]: Successfully loaded hotbars", BetterBuilding.getInstance());
        } catch (IOException | ClassNotFoundException ignored) { }
        return hotbars;
    }
}