package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * {@code economy} 섹션: 잔액(조회만, 없는 계좌를 만들지 않음)·화폐 이름, 접속 중이면 인벤토리 동전 합계.
 */
public class EconomyProvider implements PlayerDataProvider {

    private final MyMinecraftPlugin plugin;

    public EconomyProvider(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String key() {
        return "economy";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        JsonObject section = new JsonObject();
        section.addProperty("balance", plugin.getEconomyManager().peekBalance(player.getUniqueId()));
        section.addProperty("currency_name", plugin.getEconomyManager().currencyName());
        Player online = player.getPlayer();
        if (online != null) {
            section.addProperty("coin_inventory_value",
                    Math.round(plugin.getCurrencyManager().getTotalCoinValue(online)));
        } else {
            section.add("coin_inventory_value", JsonNull.INSTANCE);
        }
        return section;
    }
}
