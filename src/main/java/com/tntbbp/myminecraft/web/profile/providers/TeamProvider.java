package com.tntbbp.myminecraft.web.profile.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.social.TeamManager;
import com.tntbbp.myminecraft.model.Team;
import com.tntbbp.myminecraft.web.PlayerNames;
import com.tntbbp.myminecraft.web.profile.PlayerDataProvider;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/** {@code team} 섹션: 소속 팀 정보. 팀이 없으면 섹션 자체가 null. */
public class TeamProvider implements PlayerDataProvider {

    private final MyMinecraftPlugin plugin;

    public TeamProvider(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String key() {
        return "team";
    }

    @Override
    public JsonObject provide(OfflinePlayer player) {
        TeamManager teamManager = plugin.getTeamManager();
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            return null;
        }
        JsonArray members = new JsonArray();
        for (UUID member : team.members()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", member.toString());
            entry.addProperty("name", PlayerNames.nameOrUuid(member));
            members.add(entry);
        }
        JsonObject section = new JsonObject();
        section.addProperty("name", team.name());
        section.addProperty("is_leader", player.getUniqueId().equals(team.leader()));
        section.addProperty("member_count", team.members().size());
        section.add("members", members);
        section.addProperty("has_core_home", teamManager.getHome(team.name()) != null);
        return section;
    }
}
