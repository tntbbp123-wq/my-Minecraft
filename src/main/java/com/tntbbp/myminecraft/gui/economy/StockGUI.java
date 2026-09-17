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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public StockGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        StockHolder holder = new StockHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        StockManager stockManager = plugin.getStockManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        NewsManager newsManager = plugin.getNewsManager();

        inventory.setItem(BALANCE_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6보유 " + economyManager.currencyName())
                .lore(List.of("§f" + format(economyManager.getBalance(player.getUniqueId())) + economyManager.currencyName()))
                .build());

        List<Stock> stocks = stockManager.getStocks();
        for (int i = 0; i < stocks.size() && i < STOCK_SLOTS.length; i++) {
            Stock stock = stocks.get(i);
            int slot = STOCK_SLOTS[i];
            int owned = stockManager.getHolding(player.getUniqueId(), stock.getId());
            double change = stock.changePercentFromPrevious();
            String changeText = (change >= 0 ? "§a▲ " : "§c▼ ") + String.format("%.2f", Math.abs(change)) + "%";

            List<String> lore = new ArrayList<>();
            lore.add("§7현재가: §f" + format(stock.getPrice()) + economyManager.currencyName());
            lore.add("§7변동: " + changeText);
            lore.add("§7보유 수량: §f" + owned + "주");
            lore.add("");
            lore.add("§a좌클릭 §7- 1주 매수 §7(쉬프트: 10주)");
            lore.add("§c우클릭 §7- 1주 매도 §7(쉬프트: 10주)");

            List<String> news = newsManager.getRevealedNewsForStock(stock.getId());
            if (!news.isEmpty()) {
                lore.add("");
                lore.add("§6§l최근 뉴스");
                for (String content : news) {
                    lore.add("§6- §f" + content);
                }
            }

            ItemStack item = new ItemBuilder(stock.getMaterial())
                    .name("§e" + stock.getName())
                    .lore(lore)
                    .build();
            inventory.setItem(slot, item);
            holder.mapSlot(slot, stock.getId());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 메뉴로 돌아가기")
                .build());

        player.openInventory(inventory);
    }

    private String format(double value) {
        return String.format("%,.1f", value);
    }
}
