package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HomeManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("homes.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public int maxHomes() {
        return plugin.getConfig().getInt("home.max-homes", 5);
    }

    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> homes = new LinkedHashMap<>();
        ConfigurationSection playerSection = data.getConfigurationSection(uuid.toString());
        if (playerSection == null) {
            return homes;
        }
        for (String name : playerSection.getKeys(false)) {
            Location loc = LocationUtil.load(playerSection.getConfigurationSection(name));
            if (loc != null) {
                homes.put(name, loc);
            }
        }
        return homes;
    }

    public Location getHome(UUID uuid, String name) {
        return getHomes(uuid).get(name.toLowerCase());
    }

    public boolean hasHome(UUID uuid, String name) {
        return getHome(uuid, name) != null;
    }

    /** @return true면 저장 성공, false면 최대 개수 초과 */
    public boolean setHome(UUID uuid, String name, Location location) {
        String key = name.toLowerCase();
        boolean exists = hasHome(uuid, key);
        if (!exists && getHomes(uuid).size() >= maxHomes()) {
            return false;
        }
        ConfigurationSection section = data.getConfigurationSection(uuid.toString());
        if (section == null) {
            section = data.createSection(uuid.toString());
        }
        ConfigurationSection homeSection = section.createSection(key);
        LocationUtil.save(homeSection, location);
        save();
        return true;
    }

    public boolean delHome(UUID uuid, String name) {
        ConfigurationSection section = data.getConfigurationSection(uuid.toString());
        String key = name.toLowerCase();
        if (section == null || !section.contains(key)) {
            return false;
        }
        section.set(key, null);
        save();
        return true;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("homes.yml 저장 실패: " + e.getMessage());
        }
    }
}
