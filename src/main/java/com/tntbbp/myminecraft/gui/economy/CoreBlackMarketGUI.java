package com.tntbbp.myminecraft.gui.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.world.CoreManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 코어 GUI의 "암시장" 메뉴. 음지형 상점 아이템 8종을 포인트로 구매할 수 있다. */
public class CoreBlackMarketGUI {

    public static final String TITLE = "§4암시장";
    public static final int SIZE = 36;

    public static final int BACK_SLOT = 31;

    private static final List<String> ITEM_NAMES = List.of(
            "일괄약탈주문서", "함정설치키트", "화염병", "연막탄",
            "밀도나침반", "발자국추적기", "혈흔나침반", "소음차단포션"
    );

    private static final int[] SLOTS = {10, 11, 12, 13, 15, 16, 17, 19};

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final String teamName;

    public CoreBlackMarketGUI(MyMinecraftPlugin plugin, Player player, String teamName) {
        this.plugin = plugin;
        this.player = player;
        this.teamName = teamName;
    }

    public void open() {
        CoreBlackMarketHolder holder = new CoreBlackMarketHolder(teamName);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        CoreManager coreManager = plugin.getCoreManager();
        String currencyName = plugin.getEconomyManager().currencyName();

        for (int i = 0; i < ITEM_NAMES.size() && i < SLOTS.length; i++) {
            String itemName = ITEM_NAMES.get(i);
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
            int slot = SLOTS[i];
            inventory.setItem(slot, icon);
            holder.mapItem(slot, itemName);
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 코어 메뉴로 돌아가기")
                .build());

        player.openInventory(inventory);
    }
}
