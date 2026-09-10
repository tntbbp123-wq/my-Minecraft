package com.tntbbp.myminecraft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class AdminMenuHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, String> slotToSuggestedCommand = new HashMap<>();
    private final Map<Integer, String> slotToGiveItemName = new HashMap<>();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void mapCommand(int slot, String suggestedCommand) {
        slotToSuggestedCommand.put(slot, suggestedCommand);
    }

    public String getSuggestedCommand(int slot) {
        return slotToSuggestedCommand.get(slot);
    }

    public void mapGiveItem(int slot, String itemName) {
        slotToGiveItemName.put(slot, itemName);
    }

    public String getGiveItemName(int slot) {
        return slotToGiveItemName.get(slot);
    }
}
