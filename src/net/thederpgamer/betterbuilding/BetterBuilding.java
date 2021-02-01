package net.thederpgamer.betterbuilding;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import api.mod.config.FileConfiguration;
import net.thederpgamer.betterbuilding.gui.BuildHotbar;
import net.thederpgamer.betterbuilding.util.HotbarUtils;
import org.apache.commons.io.IOUtils;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;
import javax.vecmath.Vector2f;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * BetterBuilding.java
 * Main class for BetterBuilding StarMade mod
 * ==================================================
 * Created 01/21/2021
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

    private static BetterBuilding instance;

    public static void main(String[] args) { }

    //Data
    public BuildHotbar buildHotbar;
    public boolean autoSaveTimerStarted = false;

    //Config
    private final String[] defaultConfig = {
            "debug-mode: false",
            "hotbar-pos: 1038, 627",
            "auto-save-interval: 3500",
            "max-symmetry-planes: 5"
    };
    public boolean debugMode = false;
    public Vector2f hotbarPos = new Vector2f(1038, 627);
    public int autoSaveInterval = 3500;
    public int maxSymmetryPlanes = 5;

    public static BetterBuilding getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        registerListeners();
        HotbarUtils.initialize();
    }

    @Override
    public byte[] onClassTransform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] byteCode) {
        if(className.endsWith("BuildModeDrawer") || className.endsWith("BuildSelection") ||
            className.endsWith("CopyArea") || className.endsWith("SegmentBuildController") ||
            className.endsWith("PlayerExternalController") || className.endsWith("PlayerInteractionControlManager") ||
            className.endsWith("EditableSendableSegmentController") || className.endsWith("AdvancedBuildModeGUISGroup") ||
            className.endsWith("AdvancedBuildModeSymmetry")) {
            return overwriteClass(className, byteCode);
        } else {
            return super.onClassTransform(loader, className, classBeingRedefined, protectionDomain, byteCode);
        }
    }

    private void loadConfig() {
        FileConfiguration config = getConfig("config");
        config.saveDefault(defaultConfig);

        debugMode = config.getConfigurableBoolean("debug-mode", false);
        String[] posString = config.getConfigurableValue("hotbar-pos", "1038, 627").split(", ");
        hotbarPos = new Vector2f(Float.parseFloat(posString[0]), Float.parseFloat(posString[1]));
        autoSaveInterval = config.getConfigurableInt("auto-save-interval", 3500);
        maxSymmetryPlanes = config.getConfigurableInt("max-symmetry-planes", 5);
    }

    private void registerListeners() {
        StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
            @Override
            public void onEvent(MousePressEvent event) {
                PlayerState playerState = GameClient.getClientPlayerState();
                if(GameClientState.isCreated() && GameClient.getControlManager().isInAnyBuildMode() && playerState.isCreativeModeEnabled()) {
                    if(buildHotbar == null) {
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    }
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);

                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping()) && event.getScrollDirection() != 0 && !buildHotbar.anyNumberKeyDown()) {
                        if(event.getScrollDirection() > 0) {
                            buildHotbar.cycleNext();
                        } else if(event.getScrollDirection() < 0) {
                            buildHotbar.cyclePrevious();
                        } else {
                            return;
                        }
                        if(!autoSaveTimerStarted) {
                            buildHotbar.startAutoSaveTimer(autoSaveInterval);
                            autoSaveTimerStarted = true;
                        }
                        event.setCanceled(true);
                    }
                } else {
                    if(buildHotbar != null) {
                        buildHotbar.setActiveHotbar(0);
                        buildHotbar.hideHotbars = true;
                    }
                }
            }
        }, this);

        StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
            @Override
            public void onEvent(KeyPressEvent event) {
                PlayerState playerState = GameClient.getClientPlayerState();
                if(GameClientState.isCreated() && GameClient.getControlManager().isInAnyBuildMode() && playerState.isCreativeModeEnabled()) {
                    if(buildHotbar == null) {
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    }
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);

                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping())) {
                        if(buildHotbar.anyNumberKeyDown() && !(Keyboard.isKeyDown(29) || Keyboard.isKeyDown(203) || Keyboard.isKeyDown(205) || Keyboard.isKeyDown(200) || Keyboard.isKeyDown(208))) {
                            buildHotbar.setActiveHotbar(buildHotbar.getHotbarNumber(event.getKey()));
                        } else if(Keyboard.isKeyDown(203)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().x -= offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(205)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().x += offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(200)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().y -= offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(208)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().y += offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        }
                        if(!autoSaveTimerStarted) {
                            buildHotbar.startAutoSaveTimer(autoSaveInterval);
                            autoSaveTimerStarted = true;
                        }
                        event.setCanceled(true);
                    }
                } else {
                    if(buildHotbar != null) {
                        buildHotbar.setActiveHotbar(0);
                        buildHotbar.hideHotbars = true;
                    }
                }
            }
        }, this);
    }

    private byte[] overwriteClass(String className, byte[] byteCode) {
        byte[] bytes = null;
        try {
            ZipInputStream file = new ZipInputStream(new FileInputStream(this.getSkeleton().getJarFile()));
            while(true) {
                ZipEntry nextEntry = file.getNextEntry();
                if(nextEntry == null) break;
                if(nextEntry.getName().endsWith(className + ".class")){
                    bytes = IOUtils.toByteArray(file);
                }
            }
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(bytes != null) {
            System.err.println("[BetterBuilding] Overwrote Class: " + className);
            return bytes;
        } else {
            return byteCode;
        }
    }
}
