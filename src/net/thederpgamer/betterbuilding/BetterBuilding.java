package net.thederpgamer.betterbuilding;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import api.mod.config.FileConfiguration;
import net.thederpgamer.betterbuilding.gui.BuildHotbar;
import net.thederpgamer.betterbuilding.manager.HotbarManager;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.inventory.VirtualCreativeModeInventory;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;

import javax.vecmath.Vector2f;
import java.io.IOException;

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
    public BuildHotbar buildHotbar;

    //Config
    private final String[] defaultConfig = {"debug-mode: false", "hotbar-pos: 1038, 627", "hotbar-save-interval: 3500", "global-hotbars: false"};
    public boolean debugMode = false;
    public Vector2f hotbarPos = new Vector2f(1038, 627);
    public int hotbarSaveInterval = 3500;
    public boolean globalHotbars = false;

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        registerListeners();
        HotbarManager.initialize();
    }

    private void loadConfig() {
        FileConfiguration config = getConfig("config");
        config.saveDefault(defaultConfig);

        debugMode = config.getConfigurableBoolean("debug-mode", false);
        String[] posString = config.getConfigurableValue("hotbar-pos", "1038, 627").split(", ");
        hotbarPos = new Vector2f(Float.parseFloat(posString[0]), Float.parseFloat(posString[1]));
        hotbarSaveInterval = config.getConfigurableInt("hotbar-save-interval", 3500);
        globalHotbars = config.getConfigurableBoolean("global-hotbars", false);
    }

    private void registerListeners() {
        StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
            @Override
            public void onEvent(MousePressEvent event) {
                PlayerState playerState = GameClient.getClientPlayerState();
                if(playerState != null && GameClient.getControlManager().isInAnyBuildMode() && (playerState.isCreativeModeEnabled() || playerState.getInventory() instanceof VirtualCreativeModeInventory)) {
                    if(buildHotbar == null)
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);

                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping()) && event.getScrollDirection() != 0 && !buildHotbar.anyNumberKeyDown()) {
                        if(event.getScrollDirection() > 0) buildHotbar.cycleNext();
                        else if(event.getScrollDirection() < 0) buildHotbar.cyclePrevious();
                        else return;
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
                if(playerState != null && !GameClient.getControlManager().getState().isInFlightMode() && (playerState.isUseCreativeMode() || playerState.getInventory() instanceof VirtualCreativeModeInventory) || playerState.getInventory().isInfinite()) {
                    if(buildHotbar == null)
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
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
                            } catch(IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(205)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().x += offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch(IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(200)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().y -= offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch(IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(208)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            int offset = 1;
                            if(Keyboard.isKeyDown(29)) offset = 30;
                            buildHotbar.bgIndexAnchor.getPos().y += offset;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch(IOException exception) {
                                exception.printStackTrace();
                            }
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
}