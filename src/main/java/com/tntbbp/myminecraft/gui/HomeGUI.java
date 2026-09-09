package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** 양조대(Brewing Stand) GUI를 사용해 다른 메뉴들과 구분되는 배경을 가진다. 슬롯이 5개뿐이라 홈도 최대 5개까지 표시한다. */
public class HomeGUI {

    public static final String TITLE = "§8홈 목록";
    public static final int SLOT_COUNT = 5;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public HomeGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        HomeHolder holder = new HomeHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.BREWING, TITLE);
        holder.setInventory(inventory);

        HomeManager homeManager = plugin.getHomeManager();
        Map<String, Location> homes = homeManager.getHomes(player.getUniqueId());

        int slot = 0;
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            if (slot >= SLOT_COUNT) {
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
            inventory.setItem(0, new ItemBuilder(Material.PAPER)
                    .name("§7저장된 홈이 없습니다.")
                    .lore(List.of("§7/sethome <이름> 으로 홈을 저장하세요."))
                    .build());
        }

        player.sendMessage(ChatColor.GRAY + "/sethome <이름>, /delhome <이름> - 최대 "
                + homeManager.maxHomes() + "개까지 저장 가능");

        player.openInventory(inventory);
    }
}
