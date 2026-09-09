package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.NewsManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 관리자 전용 안내/현황 GUI. 클릭 시 해당 명령어를 채팅창에 자동으로 입력해준다. */
public class AdminMenuGUI {

    public static final String TITLE = "§4관리자 메뉴";
    public static final int SIZE = 27;

    public static final int STOCK_ADD_SLOT = 10;
    public static final int NEWS_SLOT = 12;
    public static final int FAKE_NEWS_SLOT = 14;
    public static final int SPECIAL_ITEM_SLOT = 16;
    public static final int STOCK_STATUS_SLOT = 22;
    public static final int CLOSE_SLOT = 26;

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
                        "§7/주식종류추가 <이름> <최소값> <최대값> <변동단위>",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(STOCK_ADD_SLOT, "/주식종류추가 ");

        inventory.setItem(NEWS_SLOT, new ItemBuilder(Material.PAPER)
                .name("§f뉴스 작성")
                .lore(List.of(
                        "§7/뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§71시간 뒤 공개, 2시간 뒤 실제 가격에 반영됩니다.",
                        "§7내용에는 뉴스 소식(사건)만 적으세요 - 등락/퍼센트는 자동으로",
                        "§7제외되고, 입력한 값으로만 내부 처리됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(NEWS_SLOT, "/뉴스작성 ");

        inventory.setItem(FAKE_NEWS_SLOT, new ItemBuilder(Material.GRAY_DYE)
                .name("§7가짜 뉴스 작성")
                .lore(List.of(
                        "§7/가짜뉴스작성 <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>",
                        "§71시간 뒤 똑같이 공개되지만 가격에는 반영되지 않습니다.",
                        "§7내용에는 뉴스 소식(사건)만 적으세요 - 등락/퍼센트는 자동으로 제외됩니다.",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(FAKE_NEWS_SLOT, "/가짜뉴스작성 ");

        inventory.setItem(SPECIAL_ITEM_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("§d특수 아이템 지급")
                .lore(List.of(
                        "§7/특수아이템소환 <아이템명> <유저> <수량>",
                        "§7클릭하면 채팅창에 명령어가 입력됩니다."
                ))
                .build());
        holder.mapCommand(SPECIAL_ITEM_SLOT, "/특수아이템소환 ");

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
