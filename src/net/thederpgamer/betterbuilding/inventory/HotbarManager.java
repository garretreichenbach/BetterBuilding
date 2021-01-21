package net.thederpgamer.betterbuilding.inventory;

import net.thederpgamer.betterbuilding.BetterBuilding;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * HotbarManager.java
 * Manages inventory hotbars
 * ==================================================
 * Created 01/21/2021
 *
 * @author TheDerpGamer
 */
public class HotbarManager {

    public int activeHotbar;
    private short[][] hotbars;

    public HotbarManager() {
        activeHotbar = 1;
        hotbars = new short[10][10];
        try {
            File hotbarsFile = new File(BetterBuilding.getInstance().getResourcesFolder().getPath() + "/hotbars.smdat");
            if(!hotbarsFile.exists()) saveToFile();
            Scanner scanner = new Scanner(hotbarsFile);
            String line = scanner.nextLine();
            String[] string = line.split(";");
            for(int j = 0; j < 10; j ++) {
                String[] elements = string[j].split(",");
                for(int i = 0; i < 10; i ++) {
                    hotbars[j][i] = Short.parseShort(elements[i]);
                }
            }
            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public short[] getElements() {
        return hotbars[activeHotbar];
    }

    public void next() {
        if(activeHotbar == 9) {
            activeHotbar = 0;
        } else {
            activeHotbar ++;
        }
    }

    public void previous() {
        if(activeHotbar == 0) {
            activeHotbar = 9;
        } else {
            activeHotbar --;
        }
    }

    public void saveActive(short[] hotbar) {
        hotbars[activeHotbar] = hotbar;
    }

    public void saveToFile() throws IOException {
        File hotbarsFile = new File(BetterBuilding.getInstance().getResourcesFolder().getPath() + "/hotbars.smdat");
        if(hotbarsFile.exists()) hotbarsFile.delete();
        hotbarsFile.createNewFile();
        FileWriter writer = new FileWriter(hotbarsFile);
        for(int j = 0; j < 10; j ++) {
            for(int i = 0; i < 10; i ++) {
                writer.write(String.valueOf(hotbars[j][i]));
                if(i != 9) writer.write(",");
            }
            if(j != 9) writer.write(";");
        }
        writer.close();
    }
}
