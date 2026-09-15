package com.tntbbp.myminecraft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class CoreHolder implements InventoryHolder {

    private final String teamName;
    private Inventory inventory;
    private final Map<Integer, String> slotToItemName = new HashMap<>();

    public CoreHolder(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void mapItem(int slot, String itemName) {
        slotToItemName.put(slot, itemName);
    }

    public String getItemName(int slot) {
        return slotToItemName.get(slot);
    }
}
