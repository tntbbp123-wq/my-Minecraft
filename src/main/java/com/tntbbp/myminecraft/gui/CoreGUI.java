package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.BlackMarketManager;
import com.tntbbp.myminecraft.manager.CoreManager;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.DrakenPierceManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 코어(팀 거점) 우클릭 시 열리는 상점 GUI. 강화석/두루마리/별가루/암시장 아이템 구매 + 직업 전직(자리표시자). */
public class CoreGUI {

    public static final String TITLE = "§b코어";
    public static final int SIZE = 45;

    public static final int JOB_CHANGE_SLOT = 40;
    public static final int CLOSE_SLOT = 44;

    /** SpecialItemCatalog와 동일한 아이템명. 레바테인/드라켄피어스/화폐 동전은 상점 판매 대상에서 제외. */
    private static final List<String> SHOP_ITEM_NAMES = List.of(
            "강화석", "별가루", "일반두루마리", "레어두루마리", "에픽두루마리", "레전더리두루마리",
            "일괄약탈주문서", "함정설치키트", "화염병", "연막탄",
            "밀도나침반", "발자국추적기", "혈흔나침반", "소음차단포션"
    );

    private static final int[] SHOP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

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
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        StarforceManager starforceManager = plugin.getStarforceManager();
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        DrakenPierceManager drakenPierceManager = plugin.getDrakenPierceManager();

        for (int i = 0; i < SHOP_ITEM_NAMES.size() && i < SHOP_SLOTS.length; i++) {
            String itemName = SHOP_ITEM_NAMES.get(i);
            SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(
                    enhanceManager, laevateinnManager, currencyManager, starforceManager, blackMarketManager,
                    drakenPierceManager, itemName, 1);
            if (resolved == null) {
                continue;
            }
            double price = coreManager.price(itemName);
            List<String> lore = new ArrayList<>(resolved.item().hasItemMeta() && resolved.item().getItemMeta().hasLore()
                    ? resolved.item().getItemMeta().getLore() : List.of());
            lore.add("");
            lore.add("§a가격: §f" + String.format("%,.0f", price) + "포인트");
            lore.add("§7클릭 시 1개 구매");
            ItemStack icon = new ItemBuilder(resolved.item()).lore(lore).build();
            int slot = SHOP_SLOTS[i];
            inventory.setItem(slot, icon);
            holder.mapItem(slot, itemName);
        }

        inventory.setItem(JOB_CHANGE_SLOT, new ItemBuilder(Material.TOTEM_OF_UNDYING)
                .name("§d직업의 전직")
                .lore(List.of("§8(미구현 - 추후 업데이트 예정)"))
                .build());

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("§c닫기")
                .build());

        player.openInventory(inventory);
    }
}
