package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.social.HomeManager;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.Map;

/** {@code homes} 섹션: 최대 홈 개수와 저장된 홈 목록(월드가 로드되지 않은 홈은 빠진다). */
public class HomeProvider implements PlayerDataProvider {

    private final MyMinecraftPlugin plugin;

    public HomeProvider(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String key() {
        return "homes";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        HomeManager homeManager = plugin.getHomeManager();
        JsonArray homes = new JsonArray();
        for (Map.Entry<String, Location> home : homeManager.getHomes(player.getUniqueId()).entrySet()) {
            Location location = home.getValue();
            JsonObject entry = new JsonObject();
            entry.addProperty("name", home.getKey());
            entry.addProperty("world", location.getWorld() == null ? null : location.getWorld().getName());
            entry.addProperty("x", location.getX());
            entry.addProperty("y", location.getY());
            entry.addProperty("z", location.getZ());
            homes.add(entry);
        }
        JsonObject section = new JsonObject();
        section.addProperty("max_homes", homeManager.maxHomes());
        section.add("homes", homes);
        return section;
    }
}
