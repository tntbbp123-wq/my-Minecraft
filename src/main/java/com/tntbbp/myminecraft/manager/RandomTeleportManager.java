package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** /rt 명령과 메뉴의 랜덤 TP 버튼이 공유하는 로직. */
public class RandomTeleportManager {

    public enum Result {
        SUCCESS,
        ON_COOLDOWN,
        NO_SAFE_LOCATION
    }

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RandomTeleportManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public int cooldownSeconds() {
        return plugin.getConfig().getInt("randomtp.cooldown-seconds", 30);
    }

    public long remainingCooldownSeconds(UUID uuid) {
        Long lastUse = cooldowns.get(uuid);
        if (lastUse == null) {
            return 0;
        }
        long remaining = cooldownSeconds() - (System.currentTimeMillis() - lastUse) / 1000;
        return Math.max(0, remaining);
    }

    public Result teleport(Player player) {
        if (!player.hasPermission("myminecraft.admin") && remainingCooldownSeconds(player.getUniqueId()) > 0) {
            return Result.ON_COOLDOWN;
        }

        Location result = findRandomLocation(player.getWorld());
        if (result == null) {
            return Result.NO_SAFE_LOCATION;
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.teleport(result);
        return Result.SUCCESS;
    }

    private Location findRandomLocation(World world) {
        int maxRadius = plugin.getConfig().getInt("randomtp.max-radius", 5000);
        int excludeSize = plugin.getConfig().getInt("randomtp.exclude-size", 500);
        int maxAttempts = plugin.getConfig().getInt("randomtp.max-attempts", 30);
        double halfExclude = excludeSize / 2.0;

        Location center = world.getSpawnLocation();
        double centerX = center.getX();
        double centerZ = center.getZ();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double dx = (Math.random() * 2 - 1) * maxRadius;
            double dz = (Math.random() * 2 - 1) * maxRadius;

            if (Math.abs(dx) <= halfExclude && Math.abs(dz) <= halfExclude) {
                continue;
            }

            double x = centerX + dx;
            double z = centerZ + dz;
            Location safe = LocationUtil.findSafeLocation(world, x, z);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }
}
