package com.tntbbp.myminecraft.gui;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 은행 GUI. 동전 구매/판매(기존 주식 거래소에서 이동)와 전부입금/전부출금 기능을 제공한다. */
public class BankGUI {

    public static final String TITLE = "§6은행";
    public static final int SIZE = 45;
    public static final int[] COIN_SLOTS = {19, 20, 21, 22, 23};
    public static final int BALANCE_SLOT = 4;
    public static final int DEPOSIT_ALL_SLOT = 30;
    public static final int WITHDRAW_ALL_SLOT = 32;
    public static final int BACK_SLOT = 8;

    private final MyMinecraftPlugin plugin;
    private final Player player;

    public BankGUI(MyMinecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        BankHolder holder = new BankHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();

        inventory.setItem(BALANCE_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6보유 " + economyManager.currencyName())
                .lore(List.of("§f" + format(economyManager.getBalance(player.getUniqueId())) + economyManager.currencyName()))
                .build());

        List<CurrencyManager.CoinDenomination> coins = currencyManager.denominations();
        for (int i = 0; i < coins.size() && i < COIN_SLOTS.length; i++) {
            CurrencyManager.CoinDenomination coin = coins.get(i);
            int slot = COIN_SLOTS[i];

            List<String> lore = new ArrayList<>();
            lore.add("§7가치: §f" + String.format("%,d", coin.value()) + economyManager.currencyName());
            lore.add("§7실물 화폐 - 플레이어 간 거래 가능");
            lore.add("");
            lore.add("§a좌클릭 §7- 구매 (" + economyManager.currencyName() + " → 동전)");
            lore.add("§c우클릭 §7- 판매 (동전 → " + economyManager.currencyName() + ")");

            ItemStack item = new ItemBuilder(coin.material())
                    .name("§" + coin.colorCode() + coin.displayName())
                    .lore(lore)
                    .build();
            if (coin.modelData() != 0) {
                ItemMeta meta = item.getItemMeta();
                meta.setCustomModelData(coin.modelData());
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            holder.mapCoinSlot(slot, coin.id());
        }

        inventory.setItem(DEPOSIT_ALL_SLOT, new ItemBuilder(Material.CHEST)
                .name("§a전부입금")
                .lore(List.of(
                        "§7인벤토리에 있는 모든 동전을",
                        "§7한 번에 " + economyManager.currencyName() + "로 환전합니다."
                ))
                .build());

        inventory.setItem(WITHDRAW_ALL_SLOT, new ItemBuilder(Material.ENDER_CHEST)
                .name("§c전부출금")
                .lore(List.of(
                        "§7보유 " + economyManager.currencyName() + " 전액을",
                        "§7큰 단위 동전부터 채워서 동전으로 환전합니다.",
                        "§7(가장 작은 동전 단위보다 적게 남는 금액은",
                        "§7" + economyManager.currencyName() + "로 그대로 남습니다)"
                ))
                .build());

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW)
                .name("§7« 메뉴로 돌아가기")
                .build());

        player.openInventory(inventory);
    }

    private String format(double value) {
        return String.format("%,.1f", value);
    }
}
