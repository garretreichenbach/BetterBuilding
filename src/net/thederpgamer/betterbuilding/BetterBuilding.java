package net.thederpgamer.betterbuilding;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import net.thederpgamer.betterbuilding.gui.BuildHotbar;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;

/**
 * BetterBuilding.java
 * Main class for BetterBuilding StarMade mod
 * ==================================================
 * Created 1/21/2021
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

    private static BetterBuilding instance;

    public static void main(String[] args) {
    }

    //Data
    private final String version = "0.1.7";
    public BuildHotbar buildHotbar;

    public static BetterBuilding getInstance() {
        return instance;
    }

    @Override
    public void onGameStart() {
        forceEnable = true;
        initialize();
    }

    @Override
    public void onEnable() {
        registerListeners();
    }

    private void initialize() {
        instance = this;
        setModName("BetterBuilding");
        setModDescription("A small mod with helpful building tools and utilities.");
        setModAuthor("TheDerpGamer");
        setModVersion(version);
    }

    private void registerListeners() {
        StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
            @Override
            public void onEvent(MousePressEvent event) {
                PlayerState playerState = GameClient.getClientPlayerState();
                if(GameClient.getControlManager().isInAnyBuildMode() && playerState.isCreativeModeEnabled()) {
                    if(buildHotbar == null) {
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    }
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);
                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping()) && event.getScrollDirection() != 0) {
                        if(event.getScrollDirection() > 0) {
                            buildHotbar.cycleNext();
                        } else if(event.getScrollDirection() < 0) {
                            buildHotbar.cyclePrevious();
                        } else {
                            return;
                        }
                        event.setCanceled(true);
                    }
                } else {
                    if(buildHotbar != null) {
                        buildHotbar.setActiveHotbar(1);
                        buildHotbar.hideHotbars = true;
                    }
                }
            }
        }, this);

        StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
            @Override
            public void onEvent(KeyPressEvent event) {
                PlayerState playerState = GameClient.getClientPlayerState();
                if(GameClient.getControlManager().isInAnyBuildMode() && playerState.isCreativeModeEnabled()) {
                    if(buildHotbar == null) {
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    }
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);
                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping()) && event.getKey() >= 48 && event.getKey() <= 57) {
                        //buildHotbar.setActiveHotbar(event.getKey() - 48);
                        //event.setCanceled(true);
                    }
                } else {
                    if(buildHotbar != null) {
                        buildHotbar.setActiveHotbar(1);
                        buildHotbar.hideHotbars = true;
                    }
                }
            }
        }, this);
    }
}
