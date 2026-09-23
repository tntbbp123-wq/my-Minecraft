package com.tntbbp.myminecraft.listener.economy;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.TradeLogger;
import com.tntbbp.myminecraft.manager.economy.BlackMarketManager;
import com.tntbbp.myminecraft.util.ItemLabels;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 암시장 아이템(일괄 약탈 주문서, 함정 설치 키트, 화염병, 연막탄) 동작 처리. */
public class BlackMarketListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public BlackMarketListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (isContainer(clicked.getType())) {
                if (blackMarketManager.isTrapKit(hand)) {
                    event.setCancelled(true);
                    handleTrapKit(player, clicked, hand);
                    return;
                }
                if (blackMarketManager.isLootAllScroll(hand) && player.isSneaking()) {
                    event.setCancelled(true);
                    handleLootAllScroll(player, clicked, hand);
                    return;
                }
            }
        }

        if (blackMarketManager.isMolotov(hand)) {
            event.setCancelled(true);
            throwTagged(player, hand, blackMarketManager.molotovKey());
        } else if (blackMarketManager.isSmokeBomb(hand)) {
            event.setCancelled(true);
            throwTagged(player, hand, blackMarketManager.smokeBombKey());
        } else if (blackMarketManager.isFootprintTracker(hand)) {
            event.setCancelled(true);
            throwTagged(player, hand, blackMarketManager.footprintTrackerKey());
        } else if (blackMarketManager.isDensityCompass(hand)) {
            event.setCancelled(true);
            handleDensityCompass(player, blackMarketManager);
        } else if (blackMarketManager.isBloodCompass(hand)) {
            event.setCancelled(true);
            handleBloodCompass(player, blackMarketManager);
        }
    }

    private void handleDensityCompass(Player player, BlackMarketManager blackMarketManager) {
        long remaining = blackMarketManager.densityCompassRemainingCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "재사용 대기 중입니다. (" + remaining + "초 후 다시 시도하세요)");
            return;
        }
        Location target = blackMarketManager.findDensityTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "주변에서 아무것도 감지되지 않았습니다.");
            return;
        }
        player.setCompassTarget(target);
        player.sendMessage(ChatColor.AQUA + "나침반이 밀집된 방향을 가리킵니다.");
    }

    private void handleBloodCompass(Player player, BlackMarketManager blackMarketManager) {
        UUID killerId = blackMarketManager.getLastKiller(player.getUniqueId());
        if (killerId == null) {
            player.sendMessage(ChatColor.GRAY + "추적할 대상이 없습니다.");
            return;
        }
        Player killer = plugin.getServer().getPlayer(killerId);
        if (killer == null || !killer.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "대상을 찾을 수 없습니다 (오프라인).");
            return;
        }
        double jitter = blackMarketManager.bloodCompassJitterRadius();
        Location target = killer.getLocation().clone().add(
                (Math.random() * 2 - 1) * jitter, 0, (Math.random() * 2 - 1) * jitter);
        player.setCompassTarget(target);
        player.sendMessage(ChatColor.DARK_RED + "나침반이 " + killer.getName() + "님의 대략적인 방향을 가리킵니다.");
    }

    private boolean isContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type == Material.BARREL || type.name().endsWith("SHULKER_BOX");
    }

    private void handleTrapKit(Player player, Block chestBlock, ItemStack kit) {
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        if (!blackMarketManager.installTrap(chestBlock, player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이 블록에는 함정을 설치할 수 없습니다.");
            return;
        }
        consumeOne(player, kit);
        player.sendMessage(ChatColor.DARK_RED + "이 상자에 함정을 설치했습니다.");
    }

    private void handleLootAllScroll(Player player, Block chestBlock, ItemStack scroll) {
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        BlackMarketManager.TrapInfo trap = blackMarketManager.findTrap(chestBlock);
        if (trap != null) {
            triggerTrap(player, trap);
        }

        if (!(chestBlock.getState() instanceof Container container)) {
            return;
        }
        Inventory blockInventory = container.getInventory();
        List<TradeLogger.ItemCount> taken = new ArrayList<>();
        for (int slot = 0; slot < blockInventory.getSize(); slot++) {
            ItemStack stack = blockInventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            // addItem이 넘긴 스택을 건드릴 수 있어 이름·개수는 옮기기 전에 적어 둔다.
            taken.add(new TradeLogger.ItemCount(ItemLabels.of(stack), stack.getAmount()));
            player.getInventory().addItem(stack).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            blockInventory.setItem(slot, null);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.7f);
        consumeOne(player, scroll);
        // 남의 상자에서 아이템(동전 = G 포함)을 통째로 가져가는 기능이라 거래 기록에 남긴다.
        if (!taken.isEmpty()) {
            plugin.getTradeLogger().blackmarketLootAll(player.getUniqueId(), player.getName(),
                    chestBlock.getWorld().getName(), chestBlock.getX(), chestBlock.getY(), chestBlock.getZ(), taken);
        }
        player.sendMessage(ChatColor.GOLD + "상자 안의 모든 아이템을 쓸어 담았습니다.");
    }

    /** 함정 소유자가 아닌 사람이 상자를 여는 순간(바닐라 열기든 일괄 약탈 주문서든) 공통으로 호출된다. */
    private void triggerTrap(Player opener, BlackMarketManager.TrapInfo trap) {
        if (opener.getUniqueId().equals(trap.owner())) {
            return;
        }
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        blackMarketManager.clearTrap(trap.container());

        if (OpImmunity.isImmune(opener)) {
            opener.sendMessage(ChatColor.GRAY + "(OP 면역) 함정이 무효화되었습니다.");
            return;
        }

        Location location = trap.container().getLocation().add(0.5, 0.5, 0.5);
        if (trap.tnt()) {
            TNTPrimed tnt = location.getWorld().spawn(location, TNTPrimed.class);
            tnt.setFuseTicks(20);
        } else {
            opener.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                    blackMarketManager.poisonDurationSeconds() * 20, 1));
            opener.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    blackMarketManager.witherDurationSeconds() * 20, 1));
            opener.getWorld().playSound(location, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.6f);
        }
        opener.sendMessage(ChatColor.DARK_RED + "함정에 걸렸습니다!");

        Player owner = plugin.getServer().getPlayer(trap.owner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.RED + opener.getName() + "님이 당신의 함정 상자를 열었습니다!");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block trapBlock = trapBlockOf(event.getInventory().getHolder());
        if (trapBlock == null) {
            return;
        }
        BlackMarketManager.TrapInfo trap = plugin.getBlackMarketManager().findTrap(trapBlock);
        if (trap != null) {
            triggerTrap(player, trap);
        }
    }

    /** 더블 상자는 홀더가 Container가 아니라 DoubleChest라서, 한쪽 반의 블록을 대신 돌려준다. */
    private Block trapBlockOf(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            return doubleChest.getLeftSide() instanceof Container leftSide ? leftSide.getBlock() : null;
        }
        return holder instanceof Container container ? container.getBlock() : null;
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void throwTagged(Player player, ItemStack handItem, org.bukkit.NamespacedKey key) {
        consumeOne(player, handItem);
        Snowball snowball = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(1.3));
        snowball.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        Location impact = snowball.getLocation();

        if (snowball.getPersistentDataContainer().has(blackMarketManager.molotovKey(), PersistentDataType.BYTE)) {
            applyMolotov(impact, blackMarketManager);
            snowball.remove();
        } else if (snowball.getPersistentDataContainer().has(blackMarketManager.smokeBombKey(), PersistentDataType.BYTE)) {
            applySmokeBomb(impact, blackMarketManager);
            snowball.remove();
        } else if (snowball.getPersistentDataContainer().has(blackMarketManager.footprintTrackerKey(), PersistentDataType.BYTE)) {
            if (event.getHitEntity() instanceof Player target) {
                blackMarketManager.markFootprint(target.getUniqueId());
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
            }
            snowball.remove();
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        BlackMarketManager blackMarketManager = plugin.getBlackMarketManager();
        if (!blackMarketManager.isSilencePotion(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        blackMarketManager.markSilenced(player.getUniqueId());
        player.sendMessage(ChatColor.DARK_GRAY + "소음 차단 효과가 적용되었습니다. ("
                + blackMarketManager.silenceDurationTicks() / 20 + "초)");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.getBlackMarketManager().recordKill(event.getEntity().getUniqueId(), killer.getUniqueId());
    }

    private void applyMolotov(Location impact, BlackMarketManager blackMarketManager) {
        double radius = blackMarketManager.molotovRadius();
        impact.getWorld().spawnParticle(Particle.FLAME, impact, 40, radius / 2, radius / 2, radius / 2, 0.05);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        impact.getWorld().playSound(impact, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f);

        Block below = impact.getBlock();
        if (below.getType().isAir() && below.getRelative(0, -1, 0).getType().isSolid()) {
            below.setType(Material.FIRE);
        }

        for (Entity entity : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !OpImmunity.isImmune(living)) {
                living.setFireTicks(Math.max(living.getFireTicks(), blackMarketManager.molotovFireTicks()));
            }
        }
    }

    private void applySmokeBomb(Location impact, BlackMarketManager blackMarketManager) {
        AreaEffectCloud cloud = impact.getWorld().spawn(impact, AreaEffectCloud.class);
        cloud.setRadius((float) blackMarketManager.smokeBombRadius());
        cloud.setDuration(blackMarketManager.smokeBombDurationTicks());
        cloud.setParticle(Particle.CAMPFIRE_COSY_SMOKE);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.BLINDNESS, blackMarketManager.smokeBombDurationTicks(), 0), true);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
    }

    /** 연막탄의 실명 효과가 실제로 적용되는 순간 OP 플레이어는 걸러낸다. */
    @EventHandler
    public void onAreaEffectApply(AreaEffectCloudApplyEvent event) {
        if (event.getEntity().getParticle() != Particle.CAMPFIRE_COSY_SMOKE) {
            return;
        }
        event.getAffectedEntities().removeIf(OpImmunity::isImmune);
    }
}
