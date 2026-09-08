package com.tntbbp.myminecraft.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {

    private LocationUtil() {
    }

    public static void save(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static Location load(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * 주어진 x,z 좌표에서 안전하게 착지할 수 있는 Y 좌표를 찾는다.
     * 발판이 단단하고, 그 위 두 칸이 비어있으며, 용암/불 등의 위험 블록이 아닌 위치를 찾는다.
     */
    public static Location findSafeLocation(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int highestY = world.getHighestBlockYAt(blockX, blockZ);

        for (int y = Math.min(highestY + 5, world.getMaxHeight() - 2); y >= world.getMinHeight() + 1; y--) {
            Block feet = world.getBlockAt(blockX, y, blockZ);
            Block head = world.getBlockAt(blockX, y + 1, blockZ);
            Block ground = world.getBlockAt(blockX, y - 1, blockZ);

            boolean groundSafe = ground.getType().isSolid()
                    && ground.getType() != Material.LAVA
                    && ground.getType() != Material.MAGMA_BLOCK;
            boolean feetSafe = !feet.getType().isSolid() && feet.getType() != Material.LAVA && feet.getType() != Material.FIRE;
            boolean headSafe = !head.getType().isSolid() && head.getType() != Material.LAVA && head.getType() != Material.FIRE;

            if (groundSafe && feetSafe && headSafe) {
                return new Location(world, blockX + 0.5, y, blockZ + 0.5);
            }
        }
        return null;
    }
}
