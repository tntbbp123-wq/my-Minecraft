package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EconomyManager;
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
    public static final int SCROLL_SLOT = 16;
    public static final int BUTTON_SLOT = 13;
    public static final int PROGRESS_SLOT = 4;

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
        inventory.setItem(SCROLL_SLOT, null);

        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        inventory.setItem(BUTTON_SLOT, new ItemBuilder(Material.ANVIL)
                .name("§e강화하기")
                .lore(List.of(
                        "§7왼쪽 칸에 강화할 아이템을,",
                        "§7가운데 칸에 §b강화석§7을 넣고 클릭하세요.",
                        "§7오른쪽 칸에 확률 강화 두루마리를 넣으면",
                        "§7등급에 따라 성공 확률이 추가로 증가합니다. (선택)",
                        "§7(최대 강화 레벨: " + enhanceManager.maxLevel() + ")"
                ))
                .build());

        refreshProgress(plugin, inventory);

        player.openInventory(inventory);
    }

    /**
     * 입력 칸의 아이템/두루마리 여부를 기준으로 "현재 레벨 -> 다음 레벨" 진행 표시를 갱신한다.
     * 아이템을 넣거나 뺄 때, 강화를 시도한 직후에 호출해서 화면을 최신 상태로 유지한다.
     */
    public static void refreshProgress(MyMinecraftPlugin plugin, Inventory inventory) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        ItemStack target = inventory.getItem(INPUT_SLOT);
        ItemStack scroll = inventory.getItem(SCROLL_SLOT);
        double scrollBonus = enhanceManager.scrollBonusOf(scroll);
        boolean useScroll = scrollBonus > 0;

        int currentLevel = enhanceManager.getLevel(target);
        boolean hasItem = target != null && !target.getType().isAir();
        boolean maxed = hasItem && currentLevel >= enhanceManager.maxLevel();

        String progressLine = maxed
                ? "§c최대 강화 레벨 도달"
                : "§a" + currentLevel + " §7━━━━━▶ §a+" + (currentLevel + 1);

        List<String> lore;
        if (!hasItem) {
            lore = List.of("§7아이템을 넣으면", "§7강화 정보가 표시됩니다.");
        } else if (maxed) {
            lore = List.of(progressLine, "§7더 이상 강화할 수 없습니다.");
        } else {
            double chance = enhanceManager.successChance(currentLevel, scrollBonus);
            double cost = enhanceManager.cost(currentLevel);
            lore = List.of(
                    progressLine,
                    "",
                    "§7성공 확률: §f" + String.format("%.1f", chance) + "%"
                            + (useScroll ? " §d(두루마리 +" + String.format("%.0f", scrollBonus) + "%)" : ""),
                    "§7필요 비용: §f" + String.format("%,.0f", cost) + economyManager.currencyName()
            );
        }

        inventory.setItem(PROGRESS_SLOT, new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("§b강화 진행 상황")
                .lore(lore)
                .build());
    }
}
