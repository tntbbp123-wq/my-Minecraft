package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.WorldSelectManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class WorldSelectGUI {

    public static final int SIZE = 27;
    public static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int PREV_SLOT = 18;
    public static final int NEXT_SLOT = 26;

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final int page;

    public WorldSelectGUI(MyMinecraftPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(0, page);
    }

    public void open() {
        WorldSelectManager worldSelectManager = plugin.getWorldSelectManager();
        List<WorldSelectManager.WorldEntry> entries = worldSelectManager.entries();
        int pageSize = Math.max(1, worldSelectManager.pageSize());
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        int currentPage = Math.min(page, maxPage);

        WorldSelectHolder holder = new WorldSelectHolder(currentPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, "§8" + worldSelectManager.title());
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        int start = currentPage * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            WorldSelectManager.WorldEntry entry = entries.get(i);
            int slot = ENTRY_SLOTS[i - start];
            inventory.setItem(slot, worldSelectManager.createDisplayItem(entry));
            holder.mapSlot(slot, entry.id());
        }

        if (currentPage > 0) {
            inventory.setItem(PREV_SLOT, new ItemBuilder(Material.ARROW)
                    .name("§b« 이전 페이지")
                    .build());
        }
        if (currentPage < maxPage) {
            inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name("§b다음 페이지 »")
                    .build());
        }

        player.openInventory(inventory);
    }
}
