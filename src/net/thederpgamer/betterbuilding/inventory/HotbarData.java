package net.thederpgamer.betterbuilding.inventory;

import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.player.inventory.InventorySlot;
import java.io.Serializable;

/**
 * HotbarData.java
 * Serializable hotbar data
 * ==================================================
 * Created 01/27/2021
 * @author TheDerpGamer
 */
public class HotbarData implements Serializable {

    private short type;
    private int count;
    private boolean infinite;
    private int metaId;
    private String multislot;
    private HotbarData[] subSlots;

    public HotbarData(InventorySlot inventorySlot) {
        if(inventorySlot != null && !inventorySlot.isEmpty()) {
            this.type = inventorySlot.getType();
            this.count = inventorySlot.count();
            this.infinite = inventorySlot.isInfinite();
            this.metaId = inventorySlot.metaId;
            this.multislot = inventorySlot.multiSlot;
            if(inventorySlot.getSubSlots() != null && inventorySlot.getSubSlots().size() > 0) {
                this.subSlots = new HotbarData[inventorySlot.getSubSlots().size()];
                for(int i = 0; i < this.subSlots.length; i ++) {
                    this.subSlots[i] = new HotbarData(inventorySlot.getSubSlots().get(i));
                }
            }
        } else {
            this.type = Element.TYPE_NONE;
            this.count = 0;
        }
    }

    public InventorySlot toInventorySlot() {
        InventorySlot inventorySlot = new InventorySlot();
        if(type != Element.TYPE_NONE) {
            inventorySlot.setType(type);
            inventorySlot.setCount(count);
            inventorySlot.setInfinite(infinite);
            inventorySlot.metaId = metaId;
            inventorySlot.multiSlot = multislot;
            if (subSlots != null) {
                for (HotbarData subSlotData : subSlots) {
                    inventorySlot.getSubSlots().add(subSlotData.toInventorySlot());
                }
            }
        } else {
            inventorySlot.setType(Element.TYPE_NONE);
        }
        return inventorySlot;
    }

    public short getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public int getMetaId() {
        return metaId;
    }

    public String getMultislot() {
        return multislot;
    }

    public HotbarData[] getSubSlots() {
        return subSlots;
    }
}
