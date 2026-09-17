package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CoreManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 코어(팀 거점) 우클릭 시 열리는 상점 GUI.
 * 메인 화면에서 강화석/두루마리/별가루를 바로 구매할 수 있고, "암시장"과 "직업 전직 및 초월"은
 * 별도 메뉴(버튼)로 분리되어 있다.
 */
public class CoreGUI {

    public static final String TITLE = "§b코어";
    public static final int SIZE = 45;

    public static final int BLACK_MARKET_SLOT = 20;
    public static final int JOB_CHANGE_SLOT = 24;
    public static final int CLOSE_SLOT = 44;

    /** SpecialItemCatalog와 동일한 아이템명. */
    private static final List<String> SHOP_ITEM_NAMES = List.of(
            "강화석", "별가루", "일반두루마리", "레어두루마리", "에픽두루마리", "레전더리두루마리"
    );

    private static final int[] SHOP_SLOTS = {10, 11, 12, 13, 14, 15};

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final String teamName;

    public CoreGUI(MyMinecraftPlugin plugin, Player player, String teamName) {
        this.plugin = plugin;
        this.player = player;
        this.teamName = teamName;
    }

    public void open() {
        CoreHolder holder = new CoreHolder(teamName);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        CoreManager coreManager = plugin.getCoreManager();
        String currencyName = plugin.getEconomyManager().currencyName();

        for (int i = 0; i < SHOP_ITEM_NAMES.size() && i < SHOP_SLOTS.length; i++) {
            String itemName = SHOP_ITEM_NAMES.get(i);
            SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(plugin, itemName, 1);
            if (resolved == null) {
                continue;
            }
            double price = coreManager.price(itemName);
            List<String> lore = new ArrayList<>(resolved.item().hasItemMeta() && resolved.item().getItemMeta().hasLore()
                    ? resolved.item().getItemMeta().getLore() : List.of());
            lore.add("");
            lore.add("§a가격: §f" + String.format("%,.0f", price) + currencyName);
            lore.add("§7클릭 시 1개 구매");
            ItemStack icon = new ItemBuilder(resolved.item()).lore(lore).build();
            int slot = SHOP_SLOTS[i];
            inventory.setItem(slot, icon);
            holder.mapItem(slot, itemName);
        }

        inventory.setItem(BLACK_MARKET_SLOT, new ItemBuilder(Material.ENDER_CHEST)
                .name("§4§l암시장")
                .lore(List.of(
                        "§7일반 상점에서 구할 수 없는",
                        "§7특수 재화·희귀 밀매품을",
                        "§7구매할 수 있는 음지형 상점입니다.",
                        "",
                        "§7클릭하면 암시장 메뉴가 열립니다."
                ))
                .build());

        inventory.setItem(JOB_CHANGE_SLOT, new ItemBuilder(Material.TOTEM_OF_UNDYING)
                .name("§d§l직업 전직 및 초월")
                .lore(List.of(
                        "§7일반 직업 계열의 1차/2차 전직 및",
                        "§7상위 단계로 도약하는 승급소입니다.",
                        "",
                        "§8(미구현 - 추후 업데이트 예정)"
                ))
                .build());

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("§c닫기")
                .build());

        player.openInventory(inventory);
    }
}
