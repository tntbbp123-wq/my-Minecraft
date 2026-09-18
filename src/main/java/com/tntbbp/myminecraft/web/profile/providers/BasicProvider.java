package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;

/**
 * {@code basic} 섹션: 접속 기록·플레이 시간·권한/차단/화이트리스트·통계는 파일에서 읽어 오프라인에도 채우고,
 * 게임모드·위치·체력·레벨·IP는 접속 중일 때만(오프라인이면 null).
 */
public class BasicProvider implements PlayerDataProvider {

    @Override
    public String key() {
        return "basic";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        JsonObject section = new JsonObject();
        section.add("first_played", epochOrNull(player.getFirstPlayed()));
        section.add("last_seen", epochOrNull(player.getLastSeen()));
        section.addProperty("playtime_seconds", statistic(player, Statistic.PLAY_ONE_MINUTE) / 20L);
        section.addProperty("is_op", player.isOp());
        section.addProperty("is_banned", player.isBanned());
        section.addProperty("is_whitelisted", player.isWhitelisted());

        Player online = player.getPlayer();
        if (online != null) {
            Location location = online.getLocation();
            section.addProperty("gamemode", online.getGameMode().name());
            section.addProperty("world", location.getWorld() == null ? null : location.getWorld().getName());
            section.addProperty("x", location.getX());
            section.addProperty("y", location.getY());
            section.addProperty("z", location.getZ());
            section.addProperty("health", online.getHealth());
            section.addProperty("level", online.getLevel());
            InetSocketAddress address = online.getAddress();
            section.addProperty("ip", address == null || address.getAddress() == null
                    ? null : address.getAddress().getHostAddress());
        } else {
            for (String field : new String[]{"gamemode", "world", "x", "y", "z", "health", "level", "ip"}) {
                section.add(field, JsonNull.INSTANCE);
            }
        }

        section.addProperty("deaths", statistic(player, Statistic.DEATHS));
        section.addProperty("mob_kills", statistic(player, Statistic.MOB_KILLS));
        section.addProperty("player_kills", statistic(player, Statistic.PLAYER_KILLS));
        return section;
    }

    /** 통계값. 기록 파일이 없거나 읽지 못하면 0. */
    private static long statistic(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static JsonElement epochOrNull(long millis) {
        return millis <= 0 ? JsonNull.INSTANCE : new JsonPrimitive(millis);
    }
}
