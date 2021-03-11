package net.thederpgamer.betterbuilding.util;

import api.DebugFile;
import api.mod.ModSkeleton;
import api.mod.config.PersistentObjectUtil;
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

    private static final boolean globalHotbars = BetterBuilding.getInstance().globalHotbars;
    private static final ModSkeleton instance = BetterBuilding.getInstance().getSkeleton();
    private static File hotbarsFile;

    public static void initialize() {
        if(globalHotbars) {
            hotbarsFile = new File(BetterBuilding.getInstance().getSkeleton().getResourcesFolder().getPath() + "/hotbars.smdat");
            if (!hotbarsFile.exists()) {
                try {
                    hotbarsFile.createNewFile();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public static void saveHotbars(HotbarData[][] hotbars) {
        if(globalHotbars) {
            try {
                FileOutputStream fileStream = new FileOutputStream(hotbarsFile);
                ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
                for (int h = 0; h < 10; h++) {
                    for (int i = 0; i < 10; i++) {
                        objectStream.writeObject(hotbars[h][i]);
                    }
                }
                objectStream.close();
                fileStream.close();
                DebugFile.log("[INFO]: Successfully saved hotbars", BetterBuilding.getInstance());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            PersistentObjectUtil.removeAllObjects(instance, hotbars.getClass());
            PersistentObjectUtil.addObject(instance, hotbars);
            PersistentObjectUtil.save(instance);
        }
    }

    public static HotbarData[][] loadHotbars() {
        if(globalHotbars) {
            HotbarData[][] hotbars = new HotbarData[10][10];
            try {
                FileInputStream fileStream = new FileInputStream(hotbarsFile);
                ObjectInputStream objectStream = new ObjectInputStream(fileStream);
                for (int h = 0; h < 10; h++) {
                    for (int i = 0; i < 10; i++) {
                        hotbars[h][i] = (HotbarData) objectStream.readObject();
                    }
                }
                objectStream.close();
                fileStream.close();
                DebugFile.log("[INFO]: Successfully loaded hotbars", BetterBuilding.getInstance());
            } catch (IOException | ClassNotFoundException ignored) { }
            return hotbars;
        } else {
            try {
                return (HotbarData[][]) PersistentObjectUtil.getObjects(instance, HotbarData[][].class).get(0);
            } catch (Exception ignored) {
                return new HotbarData[10][10];
            }
        }
    }
}