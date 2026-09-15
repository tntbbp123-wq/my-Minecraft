package com.tntbbp.myminecraft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StockHolder implements InventoryHolder {

    private Inventory inventory;
    private final UUID owner;
    private final Map<Integer, String> slotToStockId = new HashMap<>();

    public StockHolder(UUID owner) {
        this.owner = owner;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getOwner() {
        return owner;
    }

    public void mapSlot(int slot, String stockId) {
        slotToStockId.put(slot, stockId);
    }

    public String getStockId(int slot) {
        return slotToStockId.get(slot);
    }
}
