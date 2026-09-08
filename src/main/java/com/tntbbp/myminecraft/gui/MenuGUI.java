package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MenuGUI {

    public static final String TITLE = "§8메인 메뉴";
    public static final int SIZE = 27;

    public static final int SPAWN_SLOT = 10;
    public static final int ENDER_CHEST_SLOT = 12;
    public static final int STOCK_SLOT = 14;
    public static final int RANDOM_TP_SLOT = 16;
    public static final int ENHANCE_SLOT = 22;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public MenuGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SPAWN_SLOT, new ItemBuilder(Material.COMPASS)
                .name("§b스폰")
                .lore(List.of("§7클릭하면 스폰으로 이동합니다."))
                .build());

        inventory.setItem(ENDER_CHEST_SLOT, new ItemBuilder(Material.ENDER_CHEST)
                .name("§5엔더상자")
                .lore(List.of("§7클릭하면 엔더상자를 엽니다."))
                .build());

        inventory.setItem(STOCK_SLOT, new ItemBuilder(Material.EMERALD)
                .name("§a주식")
                .lore(List.of("§7클릭하면 주식 거래소를 엽니다."))
                .build());

        inventory.setItem(RANDOM_TP_SLOT, new ItemBuilder(Material.ENDER_PEARL)
                .name("§d랜덤 TP")
                .lore(List.of("§7클릭하면 랜덤한 위치로 이동합니다.", "§7(중앙 지역 제외)"))
                .build());

        inventory.setItem(ENHANCE_SLOT, new ItemBuilder(Material.ANVIL)
                .name("§e대장간 강화")
                .lore(List.of("§7클릭하면 아이템 강화 창을 엽니다."))
                .build());

        player.openInventory(inventory);
    }
}
