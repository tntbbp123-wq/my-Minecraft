package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 디스펜서(Dispenser) GUI를 사용해 다른 메뉴들과 구분되는 배경을 가진다. */
public class StockGUI {

    public static final String TITLE = "§8주식 거래소";
    public static final int BALANCE_SLOT = 0;
    public static final int[] STOCK_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    public static final int BACK_SLOT = 8;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public StockGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        StockHolder holder = new StockHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.DISPENSER, TITLE);
        holder.setInventory(inventory);

        StockManager stockManager = plugin.getStockManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        inventory.setItem(BALANCE_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6보유 " + economyManager.currencyName())
                .lore(List.of("§f" + format(economyManager.getBalance(player.getUniqueId())) + economyManager.currencyName()))
                .customModelData(MenuIcons.BALANCE)
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

            ItemStack item = new ItemBuilder(stock.getMaterial())
                    .name("§e" + stock.getName())
                    .lore(lore)
                    .customModelData(MenuIcons.STOCK_ITEM)
                    .build();
            inventory.setItem(slot, item);
            holder.mapSlot(slot, stock.getId());
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 메뉴로 돌아가기")
                .customModelData(MenuIcons.BACK)
                .build());

        player.openInventory(inventory);
    }

    private String format(double value) {
        return String.format("%,.1f", value);
    }
}
