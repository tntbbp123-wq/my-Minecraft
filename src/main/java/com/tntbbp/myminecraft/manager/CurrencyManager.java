package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 실물 화폐(동전) 아이템 정의. 주식 거래소에서 보유 포인트를 동전으로 환전(구매)하거나,
 * 동전을 다시 포인트로 환전(판매)할 수 있다. 동전 자체는 일반 아이템이라 플레이어 간에도
 * 주고받거나 거래할 수 있다.
 */
public class CurrencyManager {

    public record CoinDenomination(String id, int value, String displayName, String colorCode, Material material, int modelData) {
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey valueKey;
    private final List<CoinDenomination> denominations = new ArrayList<>();

    public CurrencyManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.valueKey = new NamespacedKey(plugin, "coin_value");
        loadDenominations();
    }

    private void loadDenominations() {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("currency.coins");
        for (Map<?, ?> raw : list) {
            String id = String.valueOf(raw.get("id"));
            int value = raw.get("value") instanceof Number n ? n.intValue() : 1;
            String name = raw.get("name") != null ? String.valueOf(raw.get("name")) : id;
            String color = raw.get("color") != null ? String.valueOf(raw.get("color")) : "f";
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                material = Material.GOLD_NUGGET;
            }
            int modelData = raw.get("model-data") instanceof Number n ? n.intValue() : 0;
            denominations.add(new CoinDenomination(id, value, name, color, material, modelData));
        }
        denominations.sort((a, b) -> Integer.compare(a.value(), b.value()));
    }

    public List<CoinDenomination> denominations() {
        return denominations;
    }

    public ItemStack createCoin(CoinDenomination coin, int amount) {
        ItemStack item = new ItemBuilder(coin.material())
                .name("§" + coin.colorCode() + coin.displayName())
                .lore(List.of(
                        "§7실물 화폐 - 플레이어 간 거래 가능",
                        "§7가치: §f" + String.format("%,d", coin.value()) + plugin.getEconomyManager().currencyName()
                ))
                .amount(amount)
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.INTEGER, coin.value());
        if (coin.modelData() != 0) {
            meta.setCustomModelData(coin.modelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCoin(ItemStack item) {
        return getCoinValue(item) != null;
    }

    public Integer getCoinValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.INTEGER);
    }

    /** 포인트를 차감하고 동전 1개를 지급한다(구매/환전). 인벤토리가 가득 차면 바닥에 드롭된다. */
    public boolean buyCoin(org.bukkit.entity.Player player, CoinDenomination coin) {
        EconomyManager economyManager = plugin.getEconomyManager();
        if (!economyManager.subtract(player.getUniqueId(), coin.value())) {
            return false;
        }
        ItemStack item = createCoin(coin, 1);
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        return true;
    }

    /** 플레이어 인벤토리에서 해당 동전 1개를 찾아 제거하고 포인트로 환전한다(판매). */
    public boolean sellCoin(org.bukkit.entity.Player player, CoinDenomination coin) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            Integer value = getCoinValue(stack);
            if (value != null && value == coin.value()) {
                if (stack.getAmount() <= 1) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                plugin.getEconomyManager().add(player.getUniqueId(), coin.value());
                return true;
            }
        }
        return false;
    }
}
