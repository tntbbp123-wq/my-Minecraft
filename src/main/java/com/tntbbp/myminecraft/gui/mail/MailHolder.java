package com.tntbbp.myminecraft.gui.mail;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class MailHolder implements InventoryHolder {

    private Inventory inventory;
    private final int page;
    private final Map<Integer, Long> slotToMailId = new HashMap<>();

    public MailHolder(int page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** 지금 보고 있는 페이지(0부터). */
    public int getPage() {
        return page;
    }

    void mapMail(int slot, long mailId) {
        slotToMailId.put(slot, mailId);
    }

    /** 그 칸의 우편 id. 우편 칸이 아니면 null. */
    public Long getMailId(int slot) {
        return slotToMailId.get(slot);
    }
}
