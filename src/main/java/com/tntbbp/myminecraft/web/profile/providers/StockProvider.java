package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/** {@code stocks} 섹션: 보유 수량이 1 이상인 종목과 현재가 기준 평가액. */
public class StockProvider implements PlayerDataProvider {

    private final MyMinecraftPlugin plugin;

    public StockProvider(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String key() {
        return "stocks";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        StockManager stockManager = plugin.getStockManager();
        UUID uuid = player.getUniqueId();
        JsonArray holdings = new JsonArray();
        double total = 0.0;
        for (Stock stock : stockManager.getStocks()) {
            int quantity = stockManager.getHolding(uuid, stock.getId());
            if (quantity <= 0) {
                continue;
            }
            double value = stock.getPrice() * quantity;
            JsonObject entry = new JsonObject();
            entry.addProperty("stock_id", stock.getId());
            entry.addProperty("stock_name", stock.getName());
            entry.addProperty("quantity", quantity);
            entry.addProperty("current_price", stock.getPrice());
            entry.addProperty("value", value);
            holdings.add(entry);
            total += value;
        }
        JsonObject section = new JsonObject();
        section.add("holdings", holdings);
        section.addProperty("total_value", total);
        return section;
    }
}
