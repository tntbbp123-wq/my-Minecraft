package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 대장간 강화 GUI. 스미딩 테이블 인벤토리(템플릿/재료/추가재료 3칸 + 결과칸)를 그대로 빌려서
 * "마법 부여" 스타일의 슬롯 배치와 화살표 진행 표시("0 ➜ +1")를 낸다.
 * 결과칸은 실제 크래프팅 결과가 아니라 클릭하면 강화를 실행하는 버튼으로 쓴다
 * (진짜 스미딩 레시피가 아니므로 GUIListener의 PrepareSmithingEvent 핸들러가 매번 이 버튼으로 덮어써야 한다).
 */
public class EnhanceGUI {

    public static final String TITLE = "§8대장간 강화";

    public static final int INPUT_SLOT = 0;
    public static final int MATERIAL_SLOT = 1;
    public static final int SCROLL_SLOT = 2;
    public static final int BUTTON_SLOT = 3;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public EnhanceGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        EnhanceHolder holder = new EnhanceHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.SMITHING, TITLE);
        holder.setInventory(inventory);

        refreshProgress(plugin, inventory);

        player.openInventory(inventory);
    }

    /** 입력/재료/두루마리 칸 상태를 기준으로 결과칸(버튼)의 "현재 레벨 ➜ 다음 레벨" 표시를 갱신한다. */
    public static void refreshProgress(MyMinecraftPlugin plugin, Inventory inventory) {
        inventory.setItem(BUTTON_SLOT, buildButtonItem(plugin, inventory));
    }

    public static ItemStack buildButtonItem(MyMinecraftPlugin plugin, Inventory inventory) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        ItemStack target = inventory.getItem(INPUT_SLOT);
        ItemStack material = inventory.getItem(MATERIAL_SLOT);
        ItemStack scroll = inventory.getItem(SCROLL_SLOT);
        double scrollBonus = enhanceManager.scrollBonusOf(scroll);
        boolean useScroll = scrollBonus > 0;

        boolean hasItem = target != null && !target.getType().isAir();
        int currentLevel = hasItem ? enhanceManager.getLevel(target) : 0;
        boolean maxed = hasItem && currentLevel >= enhanceManager.maxLevel();

        String progressLine = maxed
                ? "§c최대 강화 레벨 도달"
                : "§a§l" + currentLevel + " §7➜ §a§l+" + (currentLevel + 1);

        List<String> lore;
        if (!hasItem) {
            lore = List.of("§7왼쪽 위 칸에 강화할 아이템을 넣어주세요.");
        } else if (maxed) {
            lore = List.of(progressLine, "§7더 이상 강화할 수 없습니다.");
        } else if (!enhanceManager.isEnhanceStone(material) || material.getAmount() < 1) {
            lore = List.of(progressLine, "", "§7왼쪽 아래 칸에 §b강화석§7을 넣어주세요.");
        } else {
            double chance = enhanceManager.successChance(currentLevel, scrollBonus);
            double cost = enhanceManager.cost(currentLevel);
            lore = List.of(
                    progressLine,
                    "",
                    "§7성공 확률: §f" + String.format("%.1f", chance) + "%"
                            + (useScroll ? " §d(두루마리 +" + String.format("%.0f", scrollBonus) + "%)" : ""),
                    "§7필요 비용: §f" + String.format("%,.0f", cost) + economyManager.currencyName(),
                    "",
                    "§e클릭하면 강화를 시도합니다."
            );
        }

        return new ItemBuilder(Material.ANVIL)
                .name("§e강화하기")
                .lore(lore)
                .build();
    }
}
