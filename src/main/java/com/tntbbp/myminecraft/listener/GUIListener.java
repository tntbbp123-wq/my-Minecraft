package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.admin.AdminItemCategory;
import com.tntbbp.myminecraft.gui.admin.AdminItemCategoryGUI;
import com.tntbbp.myminecraft.gui.admin.AdminMenuGUI;
import com.tntbbp.myminecraft.gui.admin.AdminMenuHolder;
import com.tntbbp.myminecraft.gui.economy.BankGUI;
import com.tntbbp.myminecraft.gui.economy.BankHolder;
import com.tntbbp.myminecraft.gui.economy.CoreBlackMarketGUI;
import com.tntbbp.myminecraft.gui.economy.CoreBlackMarketHolder;
import com.tntbbp.myminecraft.gui.economy.StockGUI;
import com.tntbbp.myminecraft.gui.economy.StockHolder;
import com.tntbbp.myminecraft.gui.item.EnhanceGUI;
import com.tntbbp.myminecraft.gui.item.EnhanceHolder;
import com.tntbbp.myminecraft.gui.item.StarforceGUI;
import com.tntbbp.myminecraft.gui.item.StarforceHolder;
import com.tntbbp.myminecraft.gui.item.TranscendAltarGUI;
import com.tntbbp.myminecraft.gui.item.TranscendAltarHolder;
import com.tntbbp.myminecraft.gui.mail.MailGUI;
import com.tntbbp.myminecraft.gui.menu.MenuGUI;
import com.tntbbp.myminecraft.gui.menu.MenuHolder;
import com.tntbbp.myminecraft.gui.social.HomeGUI;
import com.tntbbp.myminecraft.gui.social.HomeHolder;
import com.tntbbp.myminecraft.gui.world.CoreGUI;
import com.tntbbp.myminecraft.gui.world.CoreHolder;
import com.tntbbp.myminecraft.manager.economy.CurrencyManager;
import com.tntbbp.myminecraft.manager.economy.EconomyManager;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.manager.item.EnhanceManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.manager.item.StarforceManager;
import com.tntbbp.myminecraft.manager.social.HomeManager;
import com.tntbbp.myminecraft.manager.world.CoreManager;
import com.tntbbp.myminecraft.manager.world.LocationsManager;
import com.tntbbp.myminecraft.manager.world.RandomTeleportManager;
import com.tntbbp.myminecraft.util.ItemLabels;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
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
        } else if (holder instanceof BankHolder bankHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleBankClick((Player) event.getWhoClicked(), bankHolder, event.getSlot(), event.getClick());
        } else if (holder instanceof EnhanceHolder) {
            handleEnhanceClick(event);
        } else if (holder instanceof TranscendAltarHolder) {
            handleTranscendClick(event);
        } else if (holder instanceof StarforceHolder) {
            handleStarforceClick(event);
        } else if (holder instanceof AdminMenuHolder adminMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleAdminMenuClick((Player) event.getWhoClicked(), adminMenuHolder, event.getSlot(), event.getClick());
        } else if (holder instanceof CoreHolder coreHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleCoreClick((Player) event.getWhoClicked(), coreHolder, event.getSlot());
        } else if (holder instanceof CoreBlackMarketHolder blackMarketHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            handleCoreBlackMarketClick((Player) event.getWhoClicked(), blackMarketHolder, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        int topSize = event.getInventory().getSize();

        if (holder instanceof MenuHolder || holder instanceof HomeHolder || holder instanceof StockHolder
                || holder instanceof AdminMenuHolder || holder instanceof CoreHolder || holder instanceof BankHolder
                || holder instanceof CoreBlackMarketHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        } else if (holder instanceof EnhanceHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < topSize && slot != EnhanceGUI.INPUT_SLOT && slot != EnhanceGUI.MATERIAL_SLOT
                        && slot != EnhanceGUI.SCROLL_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> EnhanceGUI.refreshProgress(plugin, event.getInventory()));
        } else if (holder instanceof TranscendAltarHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < topSize && slot != TranscendAltarGUI.INPUT_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> TranscendAltarGUI.refreshProgress(plugin, event.getInventory()));
        } else if (holder instanceof StarforceHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < topSize && slot != StarforceGUI.INPUT_SLOT && slot != StarforceGUI.STARDUST_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> StarforceGUI.refreshProgress(plugin, event.getInventory()));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof EnhanceHolder) {
            Player player = (Player) event.getPlayer();
            returnItem(player, event.getInventory().getItem(EnhanceGUI.INPUT_SLOT));
            returnItem(player, event.getInventory().getItem(EnhanceGUI.MATERIAL_SLOT));
            returnItem(player, event.getInventory().getItem(EnhanceGUI.SCROLL_SLOT));
            event.getInventory().setItem(EnhanceGUI.INPUT_SLOT, null);
            event.getInventory().setItem(EnhanceGUI.MATERIAL_SLOT, null);
            event.getInventory().setItem(EnhanceGUI.SCROLL_SLOT, null);
        } else if (holder instanceof TranscendAltarHolder) {
            Player player = (Player) event.getPlayer();
            returnItem(player, event.getInventory().getItem(TranscendAltarGUI.INPUT_SLOT));
            event.getInventory().setItem(TranscendAltarGUI.INPUT_SLOT, null);
        } else if (holder instanceof StarforceHolder) {
            Player player = (Player) event.getPlayer();
            returnItem(player, event.getInventory().getItem(StarforceGUI.INPUT_SLOT));
            returnItem(player, event.getInventory().getItem(StarforceGUI.STARDUST_SLOT));
            event.getInventory().setItem(StarforceGUI.INPUT_SLOT, null);
            event.getInventory().setItem(StarforceGUI.STARDUST_SLOT, null);
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
            case MenuGUI.BANK_SLOT -> new BankGUI(plugin, player).open();
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
            case MenuGUI.TRANSCEND_SLOT -> new TranscendAltarGUI(plugin, player).open();
            case MenuGUI.STARFORCE_SLOT -> new StarforceGUI(plugin, player).open();
            case MenuGUI.MAIL_SLOT -> new MailGUI(plugin, player).open();
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

    private void handleCoinClick(Player player, String coinId, boolean isBuy) {
        CurrencyManager currencyManager = plugin.getCurrencyManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        CurrencyManager.CoinDenomination coin = currencyManager.denominations().stream()
                .filter(c -> c.id().equals(coinId))
                .findFirst()
                .orElse(null);
        if (coin == null) {
            return;
        }

        if (isBuy) {
            if (currencyManager.buyCoin(player, coin)) {
                player.sendMessage(ChatColor.GREEN + "구매 완료: " + coin.displayName() + " (보유 "
                        + economyManager.currencyName() + ": "
                        + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
            } else {
                player.sendMessage(ChatColor.RED + economyManager.currencyName() + "가 부족합니다.");
            }
        } else {
            if (currencyManager.sellCoin(player, coin)) {
                player.sendMessage(ChatColor.GREEN + "판매 완료: " + coin.displayName() + " (보유 "
                        + economyManager.currencyName() + ": "
                        + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
            } else {
                player.sendMessage(ChatColor.RED + "해당 동전을 보유하고 있지 않습니다.");
            }
        }
        new BankGUI(plugin, player).open();
    }

    private void handleBankClick(Player player, BankHolder holder, int slot, ClickType clickType) {
        if (slot == BankGUI.BACK_SLOT) {
            new MenuGUI(plugin, player).open();
            return;
        }
        if (slot == BankGUI.DEPOSIT_ALL_SLOT) {
            CurrencyManager currencyManager = plugin.getCurrencyManager();
            EconomyManager economyManager = plugin.getEconomyManager();
            double deposited = currencyManager.depositAll(player);
            if (deposited > 0) {
                player.sendMessage(ChatColor.GREEN + String.format("%,.0f", deposited) + economyManager.currencyName()
                        + " 입금 완료 (보유 " + economyManager.currencyName() + ": "
                        + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
            } else {
                player.sendMessage(ChatColor.RED + "입금할 동전이 없습니다.");
            }
            new BankGUI(plugin, player).open();
            return;
        }
        if (slot == BankGUI.WITHDRAW_ALL_SLOT) {
            CurrencyManager currencyManager = plugin.getCurrencyManager();
            EconomyManager economyManager = plugin.getEconomyManager();
            double withdrawn = currencyManager.withdrawAll(player);
            if (withdrawn > 0) {
                player.sendMessage(ChatColor.GREEN + String.format("%,.0f", withdrawn) + economyManager.currencyName()
                        + " 출금 완료 (보유 " + economyManager.currencyName() + ": "
                        + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
            } else {
                player.sendMessage(ChatColor.RED + "출금할 수 있는 금액이 부족합니다.");
            }
            new BankGUI(plugin, player).open();
            return;
        }

        String coinId = holder.getCoinId(slot);
        if (coinId != null) {
            handleCoinClick(player, coinId, clickType.isLeftClick());
        }
    }

    private void handleAdminMenuClick(Player player, AdminMenuHolder holder, int slot, ClickType clickType) {
        // 메뉴를 연 뒤 관리자 권한이 빠졌을 수 있다. 여기서 아이템과 G(화폐)가 나가므로 누를 때마다 다시 본다.
        if (!player.hasPermission("myminecraft.admin")) {
            player.closeInventory();
            return;
        }

        AdminMenuHolder.Nav nav = holder.getNav(slot);
        if (nav != null) {
            switch (nav) {
                case CLOSE -> player.closeInventory();
                case BACK -> new AdminMenuGUI(plugin, player).open();
                case PREV_PAGE -> new AdminItemCategoryGUI(plugin, player, holder.getCategory(),
                        holder.getPage() - 1).open();
                case NEXT_PAGE -> new AdminItemCategoryGUI(plugin, player, holder.getCategory(),
                        holder.getPage() + 1).open();
            }
            return;
        }

        AdminItemCategory category = holder.getCategoryButton(slot);
        if (category != null) {
            new AdminItemCategoryGUI(plugin, player, category, 0).open();
            return;
        }

        String giveItemName = holder.getGiveItemName(slot);
        if (giveItemName != null) {
            SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(plugin, giveItemName, 1);
            if (resolved == null) {
                return;
            }
            ItemStack item = resolved.item();
            int amount = clickType.isShiftClick() ? Math.min(64, item.getMaxStackSize()) : 1;
            item.setAmount(amount);
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            logAdminMenuTake(player, giveItemName, amount);
            player.sendMessage(ChatColor.GREEN + resolved.displayName() + " " + amount + "개를 받았습니다.");
            return;
        }

        String suggestedCommand = holder.getSuggestedCommand(slot);
        if (suggestedCommand == null) {
            return;
        }

        player.closeInventory();
        TextComponent message = new TextComponent(ChatColor.YELLOW + "아래를 클릭하면 채팅창에 명령어가 입력됩니다: "
                + ChatColor.WHITE + suggestedCommand);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestedCommand));
        player.spigot().sendMessage(message);
    }

    /**
     * 관리자 메뉴에서 꺼낸 아이템을 거래 기록에 남긴다. {@code /특수아이템소환}과 같은 종류
     * {@code admin_item_grant}와 같은 필드를 쓴다 (받는 사람이 꺼낸 관리자 자신).
     * 화폐 동전은 곧 G라서, 메뉴로 꺼낸 것도 빠짐없이 남아야 웹 관리자의 거래 기록과 맞는다.
     */
    private void logAdminMenuTake(Player player, String itemName, int amount) {
        JsonObject data = new JsonObject();
        data.addProperty("actor", player.getName());
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", player.getName());
        data.addProperty("item_name", itemName);
        data.addProperty("count", amount);
        plugin.getTradeLogger().record("admin_item_grant", player.getName(), data);
    }

    private void handleCoreClick(Player player, CoreHolder holder, int slot) {
        if (slot == CoreGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == CoreGUI.JOB_CHANGE_SLOT) {
            player.sendMessage(ChatColor.GRAY + "직업 전직 및 초월은 아직 구현되지 않았습니다.");
            return;
        }
        if (slot == CoreGUI.BLACK_MARKET_SLOT) {
            new CoreBlackMarketGUI(plugin, player, holder.getTeamName()).open();
            return;
        }

        String itemName = holder.getItemName(slot);
        if (itemName == null) {
            return;
        }
        purchaseCoreItem(player, itemName, ShopSource.CORE);
    }

    private void handleCoreBlackMarketClick(Player player, CoreBlackMarketHolder holder, int slot) {
        if (slot == CoreBlackMarketGUI.BACK_SLOT) {
            new CoreGUI(plugin, player, holder.getTeamName()).open();
            return;
        }

        String itemName = holder.getItemName(slot);
        if (itemName == null) {
            return;
        }
        purchaseCoreItem(player, itemName, ShopSource.BLACKMARKET);
    }

    /** 코어 구매가 어느 GUI에서 왔는지(거래 기록 유형 shop_core / shop_blackmarket 구분). */
    private enum ShopSource {
        CORE, BLACKMARKET
    }

    /** 코어(및 암시장) GUI 공용 구매 처리: 포인트를 차감하고 SpecialItemCatalog 기준 아이템을 지급한다. */
    private void purchaseCoreItem(Player player, String itemName, ShopSource source) {
        CoreManager coreManager = plugin.getCoreManager();
        EconomyManager economyManager = plugin.getEconomyManager();
        double price = coreManager.price(itemName);
        if (!economyManager.subtract(player.getUniqueId(), price)) {
            player.sendMessage(ChatColor.RED + economyManager.currencyName() + "가 부족합니다. (필요: "
                    + String.format("%,.0f", price) + ")");
            return;
        }

        SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(plugin, itemName, 1);
        if (resolved == null) {
            economyManager.add(player.getUniqueId(), price);
            return;
        }

        player.getInventory().addItem(resolved.item()).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        if (source == ShopSource.BLACKMARKET) {
            plugin.getTradeLogger().shopBlackmarket(player.getUniqueId(), player.getName(), itemName, price);
        } else {
            plugin.getTradeLogger().shopCore(player.getUniqueId(), player.getName(), itemName, price);
        }
        player.sendMessage(ChatColor.GREEN + resolved.displayName() + " 1개를 구매했습니다. (보유 "
                + economyManager.currencyName() + ": "
                + String.format("%,.1f", economyManager.getBalance(player.getUniqueId())) + ")");
    }

    private void handleEnhanceClick(InventoryClickEvent event) {
        Inventory topInventory = event.getInventory();
        boolean isTopInventory = event.getClickedInventory() != null
                && event.getClickedInventory() == topInventory;

        if (!isTopInventory) {
            // 플레이어 인벤토리에서 쉬프트클릭으로 넣는 경우: 입력/재료 칸에 들어갈 수 있으므로 갱신만 예약한다.
            Bukkit.getScheduler().runTask(plugin, () -> EnhanceGUI.refreshProgress(plugin, topInventory));
            return;
        }

        int slot = event.getRawSlot();
        if (slot == EnhanceGUI.INPUT_SLOT || slot == EnhanceGUI.MATERIAL_SLOT || slot == EnhanceGUI.SCROLL_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> EnhanceGUI.refreshProgress(plugin, topInventory));
            return;
        }

        event.setCancelled(true);

        if (slot == EnhanceGUI.BUTTON_SLOT) {
            runEnhance((Player) event.getWhoClicked(), event);
        }
    }

    private void runEnhance(Player player, InventoryClickEvent event) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        CurrencyManager currencyManager = plugin.getCurrencyManager();

        ItemStack targetItem = event.getInventory().getItem(EnhanceGUI.INPUT_SLOT);
        ItemStack material = event.getInventory().getItem(EnhanceGUI.MATERIAL_SLOT);
        ItemStack scroll = event.getInventory().getItem(EnhanceGUI.SCROLL_SLOT);
        double scrollBonus = enhanceManager.scrollBonusOf(scroll);
        boolean useScroll = scrollBonus > 0;

        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "강화할 아이템을 왼쪽 칸에 넣어주세요.");
            return;
        }
        int currentLevel = enhanceManager.getLevel(targetItem);
        int requiredStones = enhanceManager.requiredStones(currentLevel);
        if (!enhanceManager.isEnhanceStone(material) || material.getAmount() < requiredStones) {
            player.sendMessage(ChatColor.RED + "강화 재료(강화석)가 부족합니다. (필요: " + requiredStones + "개)");
            return;
        }

        if (currentLevel >= enhanceManager.maxLevel(targetItem)) {
            player.sendMessage(ChatColor.RED + "이미 최대 강화 레벨입니다.");
            return;
        }

        double cost = enhanceManager.cost(currentLevel);
        if (!currencyManager.chargeCoins(player, cost)) {
            player.sendMessage(ChatColor.RED + "동전이 부족합니다. (필요: "
                    + String.format("%,.0f", cost) + "상당)");
            return;
        }

        material.setAmount(material.getAmount() - requiredStones);
        event.getInventory().setItem(EnhanceGUI.MATERIAL_SLOT, material.getAmount() <= 0 ? null : material);

        if (useScroll) {
            scroll.setAmount(scroll.getAmount() - 1);
            event.getInventory().setItem(EnhanceGUI.SCROLL_SLOT, scroll.getAmount() <= 0 ? null : scroll);
        }

        boolean success = enhanceManager.rollSuccess(currentLevel, scrollBonus);
        // 동전(G)과 강화석은 성공·실패와 관계없이 이미 나갔다. 서버에서 G가 가장 많이 빠지는 곳이라
        // 거래 기록에 없으면 웹 관리자에서 돈 흐름이 맞지 않는다.
        plugin.getTradeLogger().enhanceCost(player.getUniqueId(), player.getName(), ItemLabels.of(targetItem),
                currentLevel, cost, requiredStones, useScroll, success);
        if (success) {
            int newLevel = currentLevel + 1;
            enhanceManager.applyEnhance(targetItem, newLevel);
            event.getInventory().setItem(EnhanceGUI.INPUT_SLOT, targetItem);
            player.sendMessage(ChatColor.GREEN + "강화 성공! 현재 강화 레벨: +" + newLevel);
        } else {
            player.sendMessage(ChatColor.RED + "강화 실패... 재료와 동전이 소모되었습니다.");
        }
        EnhanceGUI.refreshProgress(plugin, event.getInventory());
    }

    private void handleTranscendClick(InventoryClickEvent event) {
        Inventory topInventory = event.getInventory();
        boolean isTopInventory = event.getClickedInventory() != null
                && event.getClickedInventory() == topInventory;

        if (!isTopInventory) {
            Bukkit.getScheduler().runTask(plugin, () -> TranscendAltarGUI.refreshProgress(plugin, topInventory));
            return;
        }

        int slot = event.getRawSlot();
        if (slot == TranscendAltarGUI.INPUT_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> TranscendAltarGUI.refreshProgress(plugin, topInventory));
            return;
        }

        event.setCancelled(true);

        if (slot == TranscendAltarGUI.BUTTON_SLOT) {
            runTranscend((Player) event.getWhoClicked(), event);
        }
    }

    private void runTranscend(Player player, InventoryClickEvent event) {
        GradeManager gradeManager = plugin.getGradeManager();
        ItemStack targetItem = event.getInventory().getItem(TranscendAltarGUI.INPUT_SLOT);

        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "초월할 무기를 왼쪽 칸에 넣어주세요.");
            return;
        }
        if (!EnhanceManager.isWeapon(targetItem.getType())) {
            player.sendMessage(ChatColor.RED + "무기만 초월할 수 있습니다.");
            return;
        }

        boolean success = gradeManager.transcend(targetItem);
        if (success) {
            event.getInventory().setItem(TranscendAltarGUI.INPUT_SLOT, targetItem);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "초월 성공! 강화 한계치가 30강으로 늘어났습니다.");
        } else {
            player.sendMessage(ChatColor.RED + "이미 초월한 무기입니다.");
        }
        TranscendAltarGUI.refreshProgress(plugin, event.getInventory());
    }

    private void handleStarforceClick(InventoryClickEvent event) {
        Inventory topInventory = event.getInventory();
        boolean isTopInventory = event.getClickedInventory() != null
                && event.getClickedInventory() == topInventory;

        if (!isTopInventory) {
            Bukkit.getScheduler().runTask(plugin, () -> StarforceGUI.refreshProgress(plugin, topInventory));
            return;
        }

        int slot = event.getRawSlot();
        if (slot == StarforceGUI.INPUT_SLOT || slot == StarforceGUI.STARDUST_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> StarforceGUI.refreshProgress(plugin, topInventory));
            return;
        }

        event.setCancelled(true);

        if (slot == StarforceGUI.BUTTON_SLOT) {
            runStarforce((Player) event.getWhoClicked(), event);
        }
    }

    private void runStarforce(Player player, InventoryClickEvent event) {
        StarforceManager starforceManager = plugin.getStarforceManager();
        ItemStack targetItem = event.getInventory().getItem(StarforceGUI.INPUT_SLOT);
        ItemStack stardust = event.getInventory().getItem(StarforceGUI.STARDUST_SLOT);

        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "성을 붙일 무기를 왼쪽 칸에 넣어주세요.");
            return;
        }
        if (!EnhanceManager.isWeapon(targetItem.getType())) {
            player.sendMessage(ChatColor.RED + "무기만 성을 붙일 수 있습니다.");
            return;
        }

        int currentStars = starforceManager.getStars(targetItem);
        if (currentStars >= starforceManager.maxStars()) {
            player.sendMessage(ChatColor.RED + "이미 최대(" + starforceManager.maxStars() + "성)입니다.");
            return;
        }

        int requiredStardust = starforceManager.stardustPerStar();
        if (!starforceManager.isStardust(stardust) || stardust.getAmount() < requiredStardust) {
            player.sendMessage(ChatColor.RED + "별가루가 부족합니다. (필요: " + requiredStardust + "개)");
            return;
        }

        stardust.setAmount(stardust.getAmount() - requiredStardust);
        event.getInventory().setItem(StarforceGUI.STARDUST_SLOT, stardust.getAmount() <= 0 ? null : stardust);

        int newStars = currentStars + 1;
        starforceManager.applyStar(targetItem, newStars);
        event.getInventory().setItem(StarforceGUI.INPUT_SLOT, targetItem);

        if (newStars >= starforceManager.maxStars()) {
            player.sendMessage(ChatColor.GOLD + "★ " + newStars + "성 달성! 이 무기의 특수 능력이 활성화됩니다.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "★ 현재 " + newStars + "성입니다.");
        }
        StarforceGUI.refreshProgress(plugin, event.getInventory());
    }
}
