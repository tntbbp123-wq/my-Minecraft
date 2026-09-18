package com.tntbbp.myminecraft.gui.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.EconomyManager;
import com.tntbbp.myminecraft.manager.economy.NewsManager;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 주식 거래소 GUI. 한 페이지에 21종목({@link #STOCK_SLOTS})씩 보여주고, 넘치면 아래 줄의 이전/다음 버튼으로 넘긴다.
 * 거래 중지된 종목은 회색 이름과 «거래 중지» 표시가 붙고 매매 안내 대신 중지 안내가 나온다.
 *
 * <p>클릭 처리: 매수·매도·뒤로가기는 {@code GUIListener}가, 페이지 넘김과 중지 종목 안내는
 * {@link StockGUIListener}가 맡는다(처음 열 때 한 번 등록).
 */
public class StockGUI {

    public static final String TITLE = "§8주식 거래소";
    public static final int SIZE = 45;
    public static final int[] STOCK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    public static final int BALANCE_SLOT = 4;
    public static final int BACK_SLOT = 8;
    public static final int PREV_SLOT = 38;
    public static final int PAGE_SLOT = 40;
    public static final int NEXT_SLOT = 42;

    private static boolean listenerRegistered;

    private final MyMinecraftPlugin plugin;
    private final Player player;
    private final int requestedPage;

    /** 지금 주식 거래소를 보고 있으면 그 페이지를, 아니면 첫 페이지를 연다(매매 후 다시 열 때 페이지 유지). */
    public StockGUI(MyMinecraftPlugin plugin, Player player) {
        this(plugin, player, currentPage(player));
    }

    /** 지정한 페이지(0부터)를 연다. 범위를 벗어나면 가장 가까운 페이지로 맞춘다. */
    public StockGUI(MyMinecraftPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.requestedPage = page;
    }

    private static int currentPage(Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof StockHolder stockHolder && stockHolder.getOwner().equals(player.getUniqueId())) {
            return stockHolder.getPage();
        }
        return 0;
    }

    private static void ensureListener(MyMinecraftPlugin plugin) {
        if (listenerRegistered) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new StockGUIListener(plugin), plugin);
        listenerRegistered = true;
    }

    public void open() {
        ensureListener(plugin);

        StockManager stockManager = plugin.getStockManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        NewsManager newsManager = plugin.getNewsManager();

        List<Stock> stocks = stockManager.getStocks();
        int perPage = STOCK_SLOTS.length;
        int page = StockPages.clampPage(requestedPage, stocks.size(), perPage);
        int pageCount = StockPages.pageCount(stocks.size(), perPage);

        StockHolder holder = new StockHolder(player.getUniqueId());
        holder.setPage(page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(BALANCE_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6보유 " + economyManager.currencyName())
                .lore(List.of("§f" + format(economyManager.getBalance(player.getUniqueId())) + economyManager.currencyName()))
                .build());

        int from = StockPages.fromIndex(page, perPage);
        int to = StockPages.toIndex(page, perPage, stocks.size());
        for (int i = from; i < to; i++) {
            Stock stock = stocks.get(i);
            int slot = STOCK_SLOTS[i - from];
            inventory.setItem(slot, stockItem(stock, stockManager, economyManager, newsManager));
            holder.mapSlot(slot, stock.getId());
        }

        if (pageCount > 1) {
            if (StockPages.hasPrevious(page)) {
                inventory.setItem(PREV_SLOT, new ItemBuilder(Material.ARROW)
                        .name("§e« 이전 페이지")
                        .lore(List.of("§7" + page + " / " + pageCount + " 페이지로"))
                        .build());
            }
            inventory.setItem(PAGE_SLOT, new ItemBuilder(Material.PAPER)
                    .name("§f" + (page + 1) + " / " + pageCount + " 페이지")
                    .lore(List.of("§7전체 " + stocks.size() + "종목"))
                    .build());
            if (StockPages.hasNext(page, stocks.size(), perPage)) {
                inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                        .name("§e다음 페이지 »")
                        .lore(List.of("§7" + (page + 2) + " / " + pageCount + " 페이지로"))
                        .build());
            }
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 메뉴로 돌아가기")
                .build());

        player.openInventory(inventory);
    }

    private ItemStack stockItem(Stock stock, StockManager stockManager, EconomyManager economyManager,
                                NewsManager newsManager) {
        int owned = stockManager.getHolding(player.getUniqueId(), stock.getId());
        double change = stock.changePercentFromPrevious();
        String changeText = (change >= 0 ? "§a▲ " : "§c▼ ") + String.format("%.2f", Math.abs(change)) + "%";

        List<String> lore = new ArrayList<>();
        lore.add("§7현재가: §f" + format(stock.getPrice()) + economyManager.currencyName());
        lore.add("§7변동: " + changeText);
        lore.add("§7보유 수량: §f" + owned + "주");
        lore.add("");
        if (stock.isHalted()) {
            lore.add("§c§l거래 중지");
            lore.add("§7지금은 이 종목을 매수·매도할 수 없습니다.");
        } else {
            lore.add("§a좌클릭 §7- 1주 매수 §7(쉬프트: 10주)");
            lore.add("§c우클릭 §7- 1주 매도 §7(쉬프트: 10주)");
        }

        List<String> news = newsManager.getRevealedNewsForStock(stock.getId());
        if (!news.isEmpty()) {
            lore.add("");
            lore.add("§6§l최근 뉴스");
            for (String content : news) {
                lore.add("§6- §f" + content);
            }
        }

        String name = stock.isHalted()
                ? "§7" + stock.getName() + " §c«거래 중지»"
                : "§e" + stock.getName();
        return new ItemBuilder(stock.getMaterial())
                .name(name)
                .lore(lore)
                .build();
    }

    private String format(double value) {
        return String.format("%,.1f", value);
    }
}
