package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TranscendAltarGUI {

    public static final String TITLE = "§8초월의 제단";
    public static final int SIZE = 27;

    public static final int INPUT_SLOT = 11;
    public static final int BUTTON_SLOT = 13;
    public static final int PROGRESS_SLOT = 15;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public TranscendAltarGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        TranscendAltarHolder holder = new TranscendAltarHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(INPUT_SLOT, null);

        inventory.setItem(BUTTON_SLOT, new ItemBuilder(Material.END_CRYSTAL)
                .name("§d초월하기")
                .lore(List.of(
                        "§7왼쪽 칸에 무기를 넣고 클릭하면",
                        "§7등급이 한 단계 상승합니다.",
                        "§7(일반 → 레어 → 유니크 → 고대 → 레전드 → 미스틱 → 마스터)",
                        "§7초월하면 강화 한계치가 20강 → 30강으로 늘어납니다.",
                        "§c무기만 초월할 수 있으며, 별도 재료/비용은 없습니다."
                ))
                .build());

        refreshProgress(plugin, inventory);

        player.openInventory(inventory);
    }

    /** 입력 칸의 아이템 등급/무기 여부를 기준으로 "현재 등급 -> 다음 등급" 진행 표시를 갱신한다. */
    public static void refreshProgress(MyMinecraftPlugin plugin, Inventory inventory) {
        GradeManager gradeManager = plugin.getGradeManager();
        ItemStack target = inventory.getItem(INPUT_SLOT);
        boolean hasItem = target != null && !target.getType().isAir();

        List<String> lore;
        if (!hasItem) {
            lore = List.of("§7무기를 넣으면", "§7초월 정보가 표시됩니다.");
        } else if (!EnhanceManager.isWeapon(target.getType())) {
            lore = List.of("§c무기만 초월할 수 있습니다.");
        } else {
            GradeManager.Grade current = gradeManager.getGrade(target);
            if (current.isMax()) {
                lore = List.of(
                        "§" + current.colorCode() + current.displayName(),
                        "§c이미 최고 등급입니다. 더 이상 초월할 수 없습니다."
                );
            } else {
                GradeManager.Grade next = current.next();
                lore = List.of(
                        "§" + current.colorCode() + current.displayName()
                                + " §7━━━━━▶ §" + next.colorCode() + next.displayName(),
                        "",
                        "§7초월하면 강화 한계치가 30강까지 늘어납니다."
                );
            }
        }

        ItemStack progressItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§d초월 정보")
                .lore(lore)
                .build();
        inventory.setItem(PROGRESS_SLOT, progressItem);
    }
}
