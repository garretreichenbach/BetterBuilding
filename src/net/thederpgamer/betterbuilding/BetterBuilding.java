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
import javax.vecmath.Vector2f;
import java.io.IOException;

/**
 * BetterBuilding.java
 * Main class for BetterBuilding StarMade mod
 * ==================================================
 * Created 01/21/2021
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

    private static BetterBuilding instance;

    public static void main(String[] args) {
    }

    //Data
    private final String version = "0.1.9";
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
                if(GameClient.getControlManager().isInAnyBuildMode() && playerState.isInfiniteInventoryVolume()) {
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
                if(GameClient.getControlManager().isInAnyBuildMode() && playerState.isInfiniteInventoryVolume()) {
                    if(buildHotbar == null) {
                        (buildHotbar = new BuildHotbar(GameClient.getClientState(), GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().getInventoryPanel())).onInit();
                    }
                    buildHotbar.hideHotbars = false;
                    GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().setBuildSideBar(buildHotbar);
                    if(Keyboard.isKeyDown(KeyboardMappings.SWITCH_FIRE_MODE.getMapping())) {
                        if(buildHotbar.anyNumberKeyDown() && !(Keyboard.isKeyDown(203) || Keyboard.isKeyDown(205) || Keyboard.isKeyDown(200) || Keyboard.isKeyDown(208))) {
                            buildHotbar.setActiveHotbar(buildHotbar.getHotbarNumber(event.getKey()));
                        } else if(Keyboard.isKeyDown(203)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            buildHotbar.bgIndexAnchor.getPos().x -= 1;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(205)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            buildHotbar.bgIndexAnchor.getPos().x += 1;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(200)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            buildHotbar.bgIndexAnchor.getPos().y += 1;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        } else if(Keyboard.isKeyDown(208)) { //Todo: This is a temporary fix for the game's bad gui scaling/positioning
                            buildHotbar.bgIndexAnchor.getPos().y -= 1;
                            buildHotbar.displayPosText();
                            try {
                                buildHotbar.setSavedPos(new Vector2f(buildHotbar.bgIndexAnchor.getPos().x, buildHotbar.bgIndexAnchor.getPos().y));
                            } catch (IOException exception) {
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
