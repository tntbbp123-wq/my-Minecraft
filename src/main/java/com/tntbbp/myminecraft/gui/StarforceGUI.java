package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class StarforceGUI {

    public static final String TITLE = "§8스타포스";
    public static final int SIZE = 27;

    public static final int INPUT_SLOT = 11;
    public static final int STARDUST_SLOT = 15;
    public static final int BUTTON_SLOT = 13;
    public static final int PROGRESS_SLOT = 4;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public StarforceGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        StarforceHolder holder = new StarforceHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(STARDUST_SLOT, null);

        StarforceManager starforceManager = plugin.getStarforceManager();
        inventory.setItem(BUTTON_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("§e성 부여하기")
                .lore(List.of(
                        "§7왼쪽 칸에 무기를,",
                        "§7오른쪽 칸에 §e별가루§7를 넣고 클릭하세요.",
                        "§7성 1개당 방어관통 " + trimZero(starforceManager.percentPerStar()) + "%가 오릅니다",
                        "§7(최대 " + starforceManager.maxStars() + "성, 방어관통 최대 "
                                + trimZero(starforceManager.maxStars() * starforceManager.percentPerStar()) + "%).",
                        "§d무기만 가능하며, " + starforceManager.maxStars() + "성을 달성하면",
                        "§d무기 종류별 특수 능력이 열립니다. (다음 업데이트 예정)"
                ))
                .build());

        refreshProgress(plugin, inventory);

        player.openInventory(inventory);
    }

    /** 입력 칸의 아이템 상태를 기준으로 "현재 성 -> 다음 성" 진행 표시를 갱신한다. */
    public static void refreshProgress(MyMinecraftPlugin plugin, Inventory inventory) {
        StarforceManager starforceManager = plugin.getStarforceManager();
        ItemStack target = inventory.getItem(INPUT_SLOT);
        boolean hasItem = target != null && !target.getType().isAir();

        List<String> lore;
        if (!hasItem) {
            lore = List.of("§7무기를 넣으면", "§7스타포스 정보가 표시됩니다.");
        } else if (!EnhanceManager.isWeapon(target.getType())) {
            lore = List.of("§c무기만 성을 붙일 수 있습니다.");
        } else {
            int currentStars = starforceManager.getStars(target);
            int maxStars = starforceManager.maxStars();
            boolean maxed = currentStars >= maxStars;

            if (maxed) {
                lore = List.of(
                        "§e" + "★".repeat(currentStars),
                        "§c이미 최대(" + maxStars + "성)입니다. 특수 능력이 활성화된 상태입니다."
                );
            } else {
                double currentPercent = currentStars * starforceManager.percentPerStar();
                double nextPercent = (currentStars + 1) * starforceManager.percentPerStar();
                lore = List.of(
                        currentStars + "성 §7━━━━━▶ " + (currentStars + 1) + "성",
                        "",
                        "§7방어관통: §f" + trimZero(currentPercent) + "% §7→ §f" + trimZero(nextPercent) + "%",
                        "§7필요 별가루: §e" + starforceManager.stardustPerStar() + "개"
                );
            }
        }

        ItemStack progressItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§b스타포스 진행 상황")
                .lore(lore)
                .build();
        inventory.setItem(PROGRESS_SLOT, progressItem);
    }

    private static String trimZero(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
