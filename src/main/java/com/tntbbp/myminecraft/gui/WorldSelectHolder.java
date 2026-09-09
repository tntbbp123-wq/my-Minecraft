package com.tntbbp.myminecraft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class WorldSelectHolder implements InventoryHolder {

    private Inventory inventory;
    private int page;
    private final Map<Integer, String> slotToEntryId = new HashMap<>();

    public WorldSelectHolder(int page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public int getPage() {
        return page;
    }

    public void mapSlot(int slot, String entryId) {
        slotToEntryId.put(slot, entryId);
    }

    public String getEntryId(int slot) {
        return slotToEntryId.get(slot);
    }
}
