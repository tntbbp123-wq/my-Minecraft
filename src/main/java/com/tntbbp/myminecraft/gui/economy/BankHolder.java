package com.tntbbp.myminecraft.gui.economy;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class BankHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, String> slotToCoinId = new HashMap<>();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void mapCoinSlot(int slot, String coinId) {
        slotToCoinId.put(slot, coinId);
    }

    public String getCoinId(int slot) {
        return slotToCoinId.get(slot);
    }
}
