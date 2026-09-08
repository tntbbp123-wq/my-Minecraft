package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** 서버 공용 스폰/로비 위치를 관리한다. */
public class LocationsManager {

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public LocationsManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("locations.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public Location getSpawn() {
        if (!data.contains("spawn")) {
            return null;
        }
        return LocationUtil.load(data.getConfigurationSection("spawn"));
    }

    public void setSpawn(Location location) {
        LocationUtil.save(data.createSection("spawn"), location);
        save();
    }

    public Location getLobby() {
        if (!data.contains("lobby")) {
            return null;
        }
        return LocationUtil.load(data.getConfigurationSection("lobby"));
    }

    public void setLobby(Location location) {
        LocationUtil.save(data.createSection("lobby"), location);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("locations.yml 저장 실패: " + e.getMessage());
        }
    }
}
