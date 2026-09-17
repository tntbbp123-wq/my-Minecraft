package com.tntbbp.myminecraft.listener.social;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.social.DiscordLinkManager;
import com.tntbbp.myminecraft.manager.social.DiscordManager;
import com.tntbbp.myminecraft.manager.social.HomeManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어가 등록해둔 홈(/홈설정) 근처에서 다른 플레이어가 블록을 부수거나 상자류를 열면,
 * 그 홈 주인이 디스코드 계정을 연동해뒀을 경우(/디스코드연동) 디스코드 DM으로 침입 알림을 보낸다.
 */
public class DiscordIntrusionListener implements Listener {

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, Long> alertCooldowns = new HashMap<>();

    public DiscordIntrusionListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        checkIntrusion(event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null) {
            return;
        }
        checkIntrusion(player, location);
    }

    private void checkIntrusion(Player actor, Location location) {
        DiscordManager discordManager = plugin.getDiscordManager();
        if (!discordManager.isEnabled()) {
            return;
        }
        HomeManager homeManager = plugin.getHomeManager();
        DiscordLinkManager linkManager = plugin.getDiscordLinkManager();
        double radius = plugin.getConfig().getDouble("discord.alert-radius", 30.0);

        for (UUID ownerId : homeManager.allPlayerIdsWithHomes()) {
            if (ownerId.equals(actor.getUniqueId())) {
                continue;
            }
            String discordId = linkManager.getLinkedDiscordId(ownerId);
            if (discordId == null) {
                continue;
            }
            if (!isNearAnyHome(homeManager, ownerId, location, radius)) {
                continue;
            }
            if (isOnCooldown(ownerId)) {
                return;
            }
            alertCooldowns.put(ownerId, System.currentTimeMillis());
            discordManager.sendDirectMessage(discordId,
                    "🚨 " + actor.getName() + "님이 당신의 홈 근처에서 활동 중입니다! ("
                            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")",
                    null);
            return;
        }
    }

    private boolean isNearAnyHome(HomeManager homeManager, UUID ownerId, Location location, double radius) {
        for (Location home : homeManager.getHomes(ownerId).values()) {
            if (home.getWorld().equals(location.getWorld()) && home.distanceSquared(location) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnCooldown(UUID ownerId) {
        Long last = alertCooldowns.get(ownerId);
        if (last == null) {
            return false;
        }
        int cooldownSeconds = plugin.getConfig().getInt("discord.alert-cooldown-seconds", 60);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }
}
