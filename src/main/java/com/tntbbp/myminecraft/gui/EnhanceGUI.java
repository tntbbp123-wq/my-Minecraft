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

import java.util.ArrayList;
import java.util.List;

/** 제련대(Smithing Table) GUI를 사용해 다른 메뉴들과 구분되는 배경을 가진다. */
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

    /**
     * 강화하기 버튼의 이름/설명을 입력 칸 상태에 맞춰 갱신한다 ("현재 레벨 -> 다음 레벨" 진행 표시 포함).
     * 아이템을 넣거나 뺄 때, 강화를 시도한 직후에 호출해서 화면을 최신 상태로 유지한다.
     */
    public static void refreshProgress(MyMinecraftPlugin plugin, Inventory inventory) {
        inventory.setItem(BUTTON_SLOT, buildButtonItem(plugin, inventory));
    }

    /**
     * 강화하기 버튼 아이템을 계산한다. 제련대는 입력 슬롯이 바뀌면 바닐라가 결과 슬롯을
     * 자체적으로 재계산하려 하므로(PrepareSmithingEvent), 이 메서드로 항상 같은 결과를
     * 만들어 결과 슬롯이 임의로 비워지지 않게 한다.
     */
    public static ItemStack buildButtonItem(MyMinecraftPlugin plugin, Inventory inventory) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        ItemStack target = inventory.getItem(INPUT_SLOT);
        ItemStack scroll = inventory.getItem(SCROLL_SLOT);
        double scrollBonus = enhanceManager.scrollBonusOf(scroll);
        boolean useScroll = scrollBonus > 0;

        int currentLevel = enhanceManager.getLevel(target);
        boolean hasItem = target != null && !target.getType().isAir();
        boolean maxed = hasItem && currentLevel >= enhanceManager.maxLevel();

        List<String> lore = new ArrayList<>();
        lore.add("§7왼쪽 칸에 강화할 아이템을,");
        lore.add("§7가운데 칸에 §b강화석§7을 넣고 클릭하세요.");
        lore.add("§7오른쪽 칸에 확률 강화 두루마리를 넣으면");
        lore.add("§7등급에 따라 성공 확률이 추가로 증가합니다. (선택)");
        lore.add("§7(최대 강화 레벨: " + enhanceManager.maxLevel() + ")");
        lore.add("");

        if (!hasItem) {
            lore.add("§7아이템을 넣으면 강화 정보가 표시됩니다.");
        } else if (maxed) {
            lore.add("§c최대 강화 레벨 도달 - 더 이상 강화할 수 없습니다.");
        } else {
            String progressLine = "§a" + currentLevel + " §7━━━━━▶ §a+" + (currentLevel + 1);
            double chance = enhanceManager.successChance(currentLevel, scrollBonus);
            double cost = enhanceManager.cost(currentLevel);
            lore.add(progressLine);
            lore.add("§7성공 확률: §f" + String.format("%.1f", chance) + "%"
                    + (useScroll ? " §d(두루마리 +" + String.format("%.0f", scrollBonus) + "%)" : ""));
            lore.add("§7필요 비용: §f" + String.format("%,.0f", cost) + economyManager.currencyName());
        }

        return new ItemBuilder(Material.ANVIL)
                .name("§e강화하기")
                .lore(lore)
                .build();
    }
}
