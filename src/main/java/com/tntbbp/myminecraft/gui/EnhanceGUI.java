package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EnhanceGUI {

    public static final String TITLE = "§8대장간 강화";
    public static final int SIZE = 27;

    public static final int INPUT_SLOT = 11;
    public static final int MATERIAL_SLOT = 15;
    public static final int BUTTON_SLOT = 13;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public EnhanceGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        EnhanceHolder holder = new EnhanceHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(MATERIAL_SLOT, null);

        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        inventory.setItem(BUTTON_SLOT, new ItemBuilder(Material.ANVIL)
                .name("§e강화하기")
                .lore(List.of(
                        "§7왼쪽 칸에 강화할 아이템을,",
                        "§7오른쪽 칸에 §f" + enhanceManager.requiredMaterial().name() + "§7 을(를) 넣고 클릭하세요.",
                        "§7(최대 강화 레벨: " + enhanceManager.maxLevel() + ")"
                ))
                .build());

        player.openInventory(inventory);
    }
}
