package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.NewsManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 관리자 전용 안내/현황 GUI. 명령어 안내 항목은 클릭 시 채팅창에 명령어를 입력해주고, 특수 아이템 항목은 클릭 시 직접 지급된다. */
public class AdminMenuGUI {

    public static final String TITLE = "§4관리자 메뉴";
    public static final int SIZE = 45;

    public static final int STOCK_GIVE_SLOT = 4;
    public static final int CLOSE_SLOT = 8;
    public static final int STOCK_ADD_SLOT = 10;
    public static final int NEWS_SLOT = 12;
    public static final int FAKE_NEWS_SLOT = 14;
    public static final int SPECIAL_ITEM_SLOT = 16;
    public static final int[] TAKE_ITEM_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    public static final int STOCK_STATUS_SLOT = 40;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public AdminMenuGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        AdminMenuHolder holder = new AdminMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(STOCK_ADD_SLOT, new ItemBuilder(Material.EMERALD)
                .name("§a주식 종목 추가")
                .lore(List.of(
                        "§7/주식종류추가 <이름> <최소값> <최대값> <변동단위> <업종>",
                        "§7시작가는 항상 최소값으로 설정됩니다.",
                        "§7업종은 AI가 뉴스를 쓸 때 참고합니다 (예: \"금 채굴 회사\").",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(STOCK_ADD_SLOT, "/주식종류추가 ");

        inventory.setItem(STOCK_GIVE_SLOT, new ItemBuilder(Material.GOLD_NUGGET)
                .name("§6주식 지급")
                .lore(List.of(
                        "§7/주식지급 <종목명> <유저> <수량>",
                        "§7대가 없이 플레이어에게 주식을 바로 지급합니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(STOCK_GIVE_SLOT, "/주식지급 ");

        inventory.setItem(NEWS_SLOT, new ItemBuilder(Material.PAPER)
                .name("§f뉴스 작성")
                .lore(List.of(
                        "§7/뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§7또는: /뉴스작성 <종목명> AI:[주제] - 등락/변동폭까지",
                        "§7종목 업종에 맞게 AI가 직접 정합니다.",
                        "§71시간 뒤 공개, 2시간 뒤 실제 가격에 반영됩니다.",
                        "§7내용에는 뉴스 소식(사건)만 들어가고, 등락/퍼센트는 자동으로 제외됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(NEWS_SLOT, "/뉴스작성 ");

        inventory.setItem(FAKE_NEWS_SLOT, new ItemBuilder(Material.GRAY_DYE)
                .name("§7가짜 뉴스 작성")
                .lore(List.of(
                        "§7/가짜뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§7또는: /가짜뉴스작성 <종목명> AI:[주제]",
                        "§71시간 뒤 똑같이 공개되지만 가격에는 반영되지 않습니다.",
                        "§7내용에는 뉴스 소식(사건)만 들어가고, 등락/퍼센트는 자동으로 제외됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(FAKE_NEWS_SLOT, "/가짜뉴스작성 ");

        inventory.setItem(SPECIAL_ITEM_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("§d다른 플레이어에게 특수 아이템 지급")
                .lore(List.of(
                        "§7/특수아이템소환 <유저> <아이템명> <수량>",
                        "§7강화석/두루마리/레바테인/화폐 동전 모두 지급 가능합니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다.",
                        "§7내 인벤토리로 바로 꺼내려면 아래 칸을 클릭하세요."
                ))
                .build());
        holder.mapCommand(SPECIAL_ITEM_SLOT, "/특수아이템소환 ");

        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        StarforceManager starforceManager = plugin.getStarforceManager();
        List<String> itemNames = SpecialItemCatalog.allItemNames(enhanceManager, currencyManager);
        for (int i = 0; i < itemNames.size() && i < TAKE_ITEM_SLOTS.length; i++) {
            String itemName = itemNames.get(i);
            SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(
                    enhanceManager, laevateinnManager, currencyManager, starforceManager, itemName, 1);
            if (resolved == null) {
                continue;
            }
            int slot = TAKE_ITEM_SLOTS[i];
            ItemStack icon = resolved.item();
            List<String> lore = new ArrayList<>(icon.hasItemMeta() && icon.getItemMeta().hasLore()
                    ? icon.getItemMeta().getLore() : List.of());
            lore.add("");
            lore.add("§a좌클릭 §7- 1개 꺼내기");
            lore.add("§a쉬프트+좌클릭 §7- 최대 스택 꺼내기");
            icon = new ItemBuilder(icon).lore(lore).build();
            inventory.setItem(slot, icon);
            holder.mapGiveItem(slot, itemName);
        }

        StockManager stockManager = plugin.getStockManager();
        NewsManager newsManager = plugin.getNewsManager();
        List<String> statusLore = new ArrayList<>();
        for (Stock stock : stockManager.getStocks()) {
            statusLore.add("§e" + stock.getName() + " §7- 현재가 " + String.format("%,.1f", stock.getPrice())
                    + (stock.isCustom() ? " §8(최소 " + (int) stock.getMinPrice() + "~최대 " + (int) stock.getMaxPrice() + ")" : ""));
        }
        statusLore.add("");
        statusLore.add("§7대기 중인 뉴스: §f" + newsManager.getPendingItems().size() + "건");
        inventory.setItem(STOCK_STATUS_SLOT, new ItemBuilder(Material.BOOK)
                .name("§b현재 종목/뉴스 현황")
                .lore(statusLore)
                .build());

        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name("§c닫기")
                .build());

        player.openInventory(inventory);
    }
}
