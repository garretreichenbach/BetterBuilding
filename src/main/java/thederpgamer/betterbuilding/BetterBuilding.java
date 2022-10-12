package thederpgamer.betterbuilding;

import api.mod.StarMod;
import thederpgamer.betterbuilding.gui.BuildHotbar;
import thederpgamer.betterbuilding.manager.ConfigManager;
import thederpgamer.betterbuilding.manager.EventManager;
import thederpgamer.betterbuilding.manager.HotbarManager;
import thederpgamer.betterbuilding.utils.DataUtils;

import javax.vecmath.Vector2f;
import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main class for BetterBuilding StarMade mod.
 *
 * @version 1.0 - [01/21/2021]
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

    //Instance
    private static BetterBuilding instance;
    public BetterBuilding() { }
    public static BetterBuilding getInstance() { return instance; }
    public static void main(String[] args) { }

    //Data
    public static Logger log;
    public BuildHotbar buildHotbar;
    public boolean[] replacedFields = new boolean[] {
            false //Advanced Build Mode Selection
    };

    //Config
    private final String[] defaultConfig = {"debug-mode: false", "hotbar-pos: 1038, 627", "hotbar-save-interval: 3500", "global-hotbars: false"};
    public boolean debugMode = false;
    public Vector2f hotbarPos = new Vector2f(1038, 627);
    public int hotbarSaveInterval = 3500;
    public boolean globalHotbars = false;

    @Override
    public void onEnable() {
        instance = this;
        ConfigManager.initialize(this);
        initLogger();
        EventManager.registerEvents(this);
        HotbarManager.initialize();
    }

    private void initLogger() {
        String logFolderPath = DataUtils.getWorldDataPath() + "/logs";
        File logsFolder = new File(logFolderPath);
        if(!logsFolder.exists()) logsFolder.mkdirs();
        else {
            if(logsFolder.listFiles() != null && logsFolder.listFiles().length > 0) {
                File[] logFiles = new File[logsFolder.listFiles().length];
                int j = logFiles.length - 1;
                for(int i = 0; i < logFiles.length && j >= 0; i++) {
                    logFiles[i] = logsFolder.listFiles()[j];
                    j--;
                }

                for(File logFile : logFiles) {
                    if(!logFile.getName().endsWith(".txt")) continue;
                    String fileName = logFile.getName().replace(".txt", "");
                    int logNumber = Integer.parseInt(fileName.substring(fileName.indexOf("log") + 3)) + 1;
                    String newName = logFolderPath + "/log" + logNumber + ".txt";
                    if(logNumber < ConfigManager.getMainConfig().getInt("max-world-logs") - 1) logFile.renameTo(new File(newName));
                    else logFile.delete();
                }
            }
        }
        try {
            File newLogFile = new File(logFolderPath + "/log0.txt");
            if(newLogFile.exists()) newLogFile.delete();
            newLogFile.createNewFile();
            log = Logger.getLogger(newLogFile.getPath());
            FileHandler handler = new FileHandler(newLogFile.getPath());
            log.addHandler(handler);
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
        } catch(IOException exception) {
            exception.printStackTrace();
        }
    }
}