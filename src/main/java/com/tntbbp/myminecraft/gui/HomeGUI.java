package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class HomeGUI {

    public static final String TITLE = "§8홈 목록";
    public static final int SIZE = 27;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public HomeGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        HomeHolder holder = new HomeHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        HomeManager homeManager = plugin.getHomeManager();
        Map<String, Location> homes = homeManager.getHomes(player.getUniqueId());

        int slot = 10;
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            if (slot > 16) {
                break;
            }
            String name = entry.getKey();
            Location loc = entry.getValue();
            ItemStack item = new ItemBuilder(Material.RED_BED)
                    .name("§b홈: " + name)
                    .lore(List.of(
                            "§7월드: " + loc.getWorld().getName(),
                            "§7좌표: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                            "",
                            "§a좌클릭 §7- 이동",
                            "§c우클릭 §7- 삭제"
                    ))
                    .build();
            inventory.setItem(slot, item);
            holder.mapSlot(slot, name);
            slot++;
        }

        if (homes.isEmpty()) {
            inventory.setItem(13, new ItemBuilder(Material.PAPER)
                    .name("§7저장된 홈이 없습니다.")
                    .lore(List.of("§7/sethome <이름> 으로 홈을 저장하세요."))
                    .build());
        }

        inventory.setItem(22, new ItemBuilder(Material.BOOK)
                .name("§e홈 관리")
                .lore(List.of(
                        "§7/sethome <이름> §f- 홈 저장",
                        "§7/delhome <이름> §f- 홈 삭제",
                        "§7최대 " + homeManager.maxHomes() + "개까지 저장 가능"
                ))
                .build());

        player.openInventory(inventory);
    }
}
