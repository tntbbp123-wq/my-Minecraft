package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.OfflinePlayer;

/** {@code discord} 섹션: 디스코드 계정 연동 여부와 디스코드 사용자 ID. */
public class DiscordProvider implements PlayerDataProvider {

    private final MyMinecraftPlugin plugin;

    public DiscordProvider(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String key() {
        return "discord";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        String discordId = plugin.getDiscordLinkManager().getLinkedDiscordId(player.getUniqueId());
        JsonObject section = new JsonObject();
        section.addProperty("linked", discordId != null);
        section.addProperty("discord_id", discordId);
        return section;
    }
}
