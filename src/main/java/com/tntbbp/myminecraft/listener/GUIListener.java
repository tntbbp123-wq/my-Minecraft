package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.*;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.manager.LocationsManager;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
import com.tntbbp.myminecraft.manager.StockManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public GUIListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof MenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleMenuClick((Player) event.getWhoClicked(), event.getSlot());
        } else if (holder instanceof HomeHolder homeHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleHomeClick((Player) event.getWhoClicked(), homeHolder, event.getSlot(), event.isRightClick());
        } else if (holder instanceof StockHolder stockHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleStockClick((Player) event.getWhoClicked(), stockHolder, event.getSlot(), event.getClick());
        } else if (holder instanceof EnhanceHolder) {
            handleEnhanceClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof EnhanceHolder) {
            Player player = (Player) event.getPlayer();
            returnItem(player, event.getInventory().getItem(EnhanceGUI.INPUT_SLOT));
            returnItem(player, event.getInventory().getItem(EnhanceGUI.MATERIAL_SLOT));
            event.getInventory().setItem(EnhanceGUI.INPUT_SLOT, null);
            event.getInventory().setItem(EnhanceGUI.MATERIAL_SLOT, null);
        }
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
    }

    private void handleMenuClick(Player player, int slot) {
        LocationsManager locationsManager = plugin.getLocationsManager();
        switch (slot) {
            case MenuGUI.SPAWN_SLOT -> {
                player.closeInventory();
                Location spawn = locationsManager.getSpawn();
                player.teleport(spawn != null ? spawn : player.getWorld().getSpawnLocation());
                player.sendMessage(ChatColor.GREEN + "스폰으로 이동했습니다.");
            }
            case MenuGUI.ENDER_CHEST_SLOT -> {
                player.closeInventory();
                player.openInventory(player.getEnderChest());
            }
            case MenuGUI.STOCK_SLOT -> new StockGUI(plugin, player).open();
            case MenuGUI.RANDOM_TP_SLOT -> {
                player.closeInventory();
                RandomTeleportManager randomTeleportManager = plugin.getRandomTeleportManager();
                RandomTeleportManager.Result result = randomTeleportManager.teleport(player);
                switch (result) {
                    case SUCCESS -> player.sendMessage(ChatColor.GREEN + "랜덤 위치로 이동했습니다.");
                    case ON_COOLDOWN -> player.sendMessage(ChatColor.RED + "쿨다운이 남아있습니다. ("
                            + randomTeleportManager.remainingCooldownSeconds(player.getUniqueId()) + "초 후 다시 시도하세요)");
                    case NO_SAFE_LOCATION -> player.sendMessage(ChatColor.RED + "안전한 위치를 찾지 못했습니다. 다시 시도해주세요.");
                }
            }
            case MenuGUI.ENHANCE_SLOT -> new EnhanceGUI(plugin, player).open();
            default -> {
            }
        }
    }

    private void handleHomeClick(Player player, HomeHolder holder, int slot, boolean rightClick) {
        String homeName = holder.getHomeName(slot);
        if (homeName == null) {
            return;
        }
        HomeManager homeManager = plugin.getHomeManager();
        if (rightClick) {
            homeManager.delHome(player.getUniqueId(), homeName);
            player.sendMessage(ChatColor.YELLOW + "'" + homeName + "' 홈을 삭제했습니다.");
            new HomeGUI(plugin, player).open();
        } else {
            Location location = homeManager.getHome(player.getUniqueId(), homeName);
            if (location != null) {
                player.closeInventory();
                player.teleport(location);
                player.sendMessage(ChatColor.GREEN + "'" + homeName + "' 홈으로 이동했습니다.");
            }
        }
    }

    private void handleStockClick(Player player, StockHolder holder, int slot, ClickType clickType) {
        if (slot == StockGUI.BACK_SLOT) {
            new MenuGUI(plugin, player).open();
            return;
        }
        String stockId = holder.getStockId(slot);
        if (stockId == null) {
            return;
        }

        StockManager stockManager = plugin.getStockManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        int amount = clickType.isShiftClick() ? 10 : 1;

        StockManager.TradeResult result;
        boolean isBuy = clickType.isLeftClick();
        if (isBuy) {
            result = stockManager.buy(player.getUniqueId(), stockId, amount);
        } else {
            result = stockManager.sell(player.getUniqueId(), stockId, amount);
        }

        switch (result) {
            case SUCCESS -> player.sendMessage(ChatColor.GREEN + (isBuy ? "매수" : "매도") + " 완료: "
                    + amount + "주 (보유 " + economyManager.currencyName() + ": "
                    + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
            case NOT_ENOUGH_BALANCE -> player.sendMessage(ChatColor.RED + economyManager.currencyName() + "가 부족합니다.");
            case NOT_ENOUGH_QUANTITY -> player.sendMessage(ChatColor.RED + "보유한 주식 수량이 부족합니다.");
            case INVALID_AMOUNT -> player.sendMessage(ChatColor.RED + "거래에 실패했습니다.");
        }
        new StockGUI(plugin, player).open();
    }

    private void handleEnhanceClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        boolean isTopInventory = event.getClickedInventory() != null
                && event.getClickedInventory() == event.getInventory();

        if (!isTopInventory) {
            return;
        }

        if (slot == EnhanceGUI.INPUT_SLOT || slot == EnhanceGUI.MATERIAL_SLOT) {
            return;
        }

        event.setCancelled(true);

        if (slot == EnhanceGUI.BUTTON_SLOT) {
            runEnhance((Player) event.getWhoClicked(), event);
        }
    }

    private void runEnhance(Player player, InventoryClickEvent event) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        EconomyManager economyManager = plugin.getEconomyManager();

        ItemStack targetItem = event.getInventory().getItem(EnhanceGUI.INPUT_SLOT);
        ItemStack material = event.getInventory().getItem(EnhanceGUI.MATERIAL_SLOT);

        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "강화할 아이템을 왼쪽 칸에 넣어주세요.");
            return;
        }
        if (material == null || material.getType() != enhanceManager.requiredMaterial() || material.getAmount() < 1) {
            player.sendMessage(ChatColor.RED + "강화 재료(" + enhanceManager.requiredMaterial().name() + ")가 필요합니다.");
            return;
        }

        int currentLevel = enhanceManager.getLevel(targetItem);
        if (currentLevel >= enhanceManager.maxLevel()) {
            player.sendMessage(ChatColor.RED + "이미 최대 강화 레벨입니다.");
            return;
        }

        double cost = enhanceManager.cost(currentLevel);
        if (!economyManager.has(player.getUniqueId(), cost)) {
            player.sendMessage(ChatColor.RED + economyManager.currencyName() + "가 부족합니다. (필요: "
                    + String.format("%,.0f", cost) + ")");
            return;
        }

        economyManager.subtract(player.getUniqueId(), cost);
        material.setAmount(material.getAmount() - 1);
        event.getInventory().setItem(EnhanceGUI.MATERIAL_SLOT, material.getAmount() <= 0 ? null : material);

        boolean success = enhanceManager.rollSuccess(currentLevel);
        if (success) {
            int newLevel = currentLevel + 1;
            enhanceManager.applyEnhance(targetItem, newLevel);
            event.getInventory().setItem(EnhanceGUI.INPUT_SLOT, targetItem);
            player.sendMessage(ChatColor.GREEN + "강화 성공! 현재 강화 레벨: +" + newLevel);
        } else {
            player.sendMessage(ChatColor.RED + "강화 실패... 재료와 " + economyManager.currencyName() + "가 소모되었습니다.");
        }
    }
}
