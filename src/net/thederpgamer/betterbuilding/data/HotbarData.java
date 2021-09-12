package net.thederpgamer.betterbuilding.data;

import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.player.inventory.InventorySlot;

/**
 * Holds serializable hotbar data.
 *
 * @version 1.0 - [01/27/2021]
 * @author TheDerpGamer
 */
public class HotbarData {

    public short type;
    public int count;
    public boolean infinite;
    public String multiSlot;
    public HotbarData[] subSlots;

    public HotbarData(InventorySlot inventorySlot) {
        if(inventorySlot != null && !inventorySlot.isEmpty() && !inventorySlot.isMetaItem()) {
            this.type = inventorySlot.getType();
            this.count = inventorySlot.count();
            this.infinite = inventorySlot.isInfinite();
            this.multiSlot = inventorySlot.multiSlot;
            if(inventorySlot.getSubSlots() != null && inventorySlot.getSubSlots().size() > 0) {
                this.subSlots = new HotbarData[inventorySlot.getSubSlots().size()];
                for(int i = 0; i < this.subSlots.length; i ++) this.subSlots[i] = new HotbarData(inventorySlot.getSubSlots().get(i));
            }
        } else {
            this.type = Element.TYPE_NONE;
            this.count = 0;
        }
    }

    /**
     * Converts this into an inventory slot.
     * @return The inventory slot
     */
    public InventorySlot convertToSlot() {
       return toInventorySlot(this);
    }

    /**
     * Converts hotbar data into an inventory slot.
     * @param hotbarData The hotbar data
     * @return The inventory slot
     */
    public static InventorySlot toInventorySlot(HotbarData hotbarData) {
        InventorySlot inventorySlot = new InventorySlot();
        if(hotbarData.type != Element.TYPE_NONE) {
            inventorySlot.setType(hotbarData.type);
            inventorySlot.setCount(hotbarData.count);
            inventorySlot.setInfinite(hotbarData.infinite);
            if(hotbarData.subSlots != null) for(HotbarData subSlotData : hotbarData.subSlots) inventorySlot.getSubSlots().add(subSlotData.convertToSlot());
        } else inventorySlot.setType(Element.TYPE_NONE);
        return inventorySlot;
    }

    /**
     * Converts an inventory slot into hotbar data.
     * @param inventorySlot The inventory slot
     * @return The hotbar data
     */
    public static HotbarData fromInventorySlot(InventorySlot inventorySlot) {
        return new HotbarData(inventorySlot);
    }
}
