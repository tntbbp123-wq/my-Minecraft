package com.tntbbp.myminecraft.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class StarforceHolder implements InventoryHolder {

    private Inventory inventory;
    private final UUID owner;

    public StarforceHolder(UUID owner) {
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
}
