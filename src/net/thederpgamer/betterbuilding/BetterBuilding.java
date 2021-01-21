package net.thederpgamer.betterbuilding;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.input.MousePressEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import net.thederpgamer.betterbuilding.inventory.HotbarManager;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;

import java.io.IOException;

/**
 * BetterBuilding.java
 * Main class for BetterBuilding StarMade mod.
 * ==================================================
 * Created 1/21/2021
 * @author TheDerpGamer
 */
public class BetterBuilding extends StarMod {

    private static BetterBuilding instance;
    public static void main(String[] args) { }

    //Data
    private final String version = "0.1.0";
    public HotbarManager hotbarManager;

    public static BetterBuilding getInstance() {
        return instance;
    }

    @Override
    public void onGameStart() {
        initialize();
    }

    @Override
    public void onEnable() {
        hotbarManager = new HotbarManager();
        registerOverwrites();
        registerListeners();
    }

    @Override
    public void onDisable() {
        try {
            hotbarManager.saveToFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initialize() {
        instance = this;
        setModName("BetterBuilding");
        setModDescription("A small mod with helpful building tools and utilities.");
        setModAuthor("TheDerpGamer");
        setModVersion(version);
    }

    private void registerOverwrites() {
        overwriteClass(KeyboardMappings.class, false);
    }

    private void registerListeners() {
        StarLoader.registerListener(MousePressEvent.class, new Listener<MousePressEvent>() {
            @Override
            public void onEvent(MousePressEvent event) {
                if(GameClient.getClientPlayerState().isCreativeModeEnabled() && Keyboard.isKeyDown(KeyboardMappings.SWAP_CREATIVE_HOTBAR.getMapping())) {
                    if(event.getScrollDirection() != 0) {
                        short[] currentElements = new short[10];
                        for(int i = 0; i < 10; i ++) {
                            currentElements[i] = GameClient.getClientPlayerState().getInventory().getSlot(i).getType();
                        }
                        hotbarManager.saveActive(currentElements);

                        if(event.getScrollDirection() > 0) {
                            hotbarManager.next();
                        } else if(event.getScrollDirection() < 0) {
                            hotbarManager.previous();
                        }

                        short[] newElements = hotbarManager.getElements();
                        for(int i = 0; i < 10; i ++) {
                            GameClient.getClientPlayerState().getInventory().getSlot(i).setType(newElements[i]);
                        }
                    }
                }
            }
        }, this);

        StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
            @Override
            public void onEvent(KeyPressEvent event) {
                if(GameClient.getClientPlayerState().isCreativeModeEnabled() && Keyboard.isKeyDown(KeyboardMappings.SWAP_CREATIVE_HOTBAR.getMapping())) {
                    if(event.getKey() >= 48 && event.getKey() <= 57) {
                        short[] currentElements = new short[10];
                        for(int i = 0; i < 10; i ++) {
                            currentElements[i] = GameClient.getClientPlayerState().getInventory().getSlot(i).getType();
                        }
                        hotbarManager.saveActive(currentElements);

                        int active = hotbarManager.activeHotbar;
                        int num = Integer.parseInt(String.valueOf(event.getChar()));
                        if(num != active) {
                            hotbarManager.activeHotbar = num;
                            short[] newElements = hotbarManager.getElements();
                            for(int i = 0; i < 10; i ++) {
                                GameClient.getClientPlayerState().getInventory().getSlot(i).setType(newElements[i]);
                            }
                        }
                    }
                }
            }
        }, this);
    }
}
