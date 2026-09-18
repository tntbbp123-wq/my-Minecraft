package com.tntbbp.myminecraft.manager.social;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.Team;
import com.tntbbp.myminecraft.util.AtomicYaml;
import com.tntbbp.myminecraft.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 팀(거점을 공유하는 단위) 관리. 팀장/팀원, 코어를 설치해 정한 팀 홈(거점)을 저장한다. */
public class TeamManager {

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public TeamManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("teams.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean teamExists(String name) {
        return data.contains(key(name));
    }

    public Set<String> teamNames() {
        ConfigurationSection section = data.getConfigurationSection("teams");
        return section == null ? new LinkedHashSet<>() : new LinkedHashSet<>(section.getKeys(false));
    }

    /** @return true면 생성 성공, false면 이름이 이미 있거나 팀장이 이미 다른 팀 소속 */
    public boolean createTeam(String name, UUID leader) {
        if (teamExists(name) || getTeamOfPlayer(leader) != null) {
            return false;
        }
        ConfigurationSection section = data.createSection(key(name));
        section.set("name", name);
        section.set("leader", leader.toString());
        section.set("members", java.util.List.of(leader.toString()));
        save();
        return true;
    }

    public boolean deleteTeam(String name) {
        if (!teamExists(name)) {
            return false;
        }
        data.set(key(name), null);
        save();
        return true;
    }

    public Team getTeam(String name) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        return section == null ? null : readTeam(name, section);
    }

    public Team getTeamOfPlayer(UUID uuid) {
        for (String teamKey : teamNames()) {
            ConfigurationSection section = data.getConfigurationSection("teams." + teamKey);
            if (section == null) {
                continue;
            }
            Team team = readTeam(section.getString("name", teamKey), section);
            if (team.members().contains(uuid)) {
                return team;
            }
        }
        return null;
    }

    /** @return true면 추가 성공, false면 팀이 없거나 대상이 이미 다른 팀 소속 */
    public boolean addMember(String name, UUID uuid) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        if (section == null || getTeamOfPlayer(uuid) != null) {
            return false;
        }
        Team team = readTeam(name, section);
        Set<UUID> updated = new LinkedHashSet<>(team.members());
        updated.add(uuid);
        section.set("members", updated.stream().map(UUID::toString).toList());
        save();
        return true;
    }

    /** @return true면 삭제 성공, false면 팀이 없거나, 팀장이거나, 팀원이 아님 */
    public boolean removeMember(String name, UUID uuid) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        if (section == null) {
            return false;
        }
        Team team = readTeam(name, section);
        if (uuid.equals(team.leader()) || !team.members().contains(uuid)) {
            return false;
        }
        Set<UUID> updated = new LinkedHashSet<>(team.members());
        updated.remove(uuid);
        section.set("members", updated.stream().map(UUID::toString).toList());
        save();
        return true;
    }

    public void setHome(String name, Location location) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        if (section == null) {
            return;
        }
        LocationUtil.save(section.createSection("home"), location);
        save();
    }

    public Location getHome(String name) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        if (section == null) {
            return null;
        }
        return LocationUtil.load(section.getConfigurationSection("home"));
    }

    public void clearHome(String name) {
        ConfigurationSection section = data.getConfigurationSection(key(name));
        if (section == null) {
            return;
        }
        section.set("home", null);
        save();
    }

    private Team readTeam(String displayName, ConfigurationSection section) {
        UUID leader = UUID.fromString(section.getString("leader"));
        Set<UUID> members = new LinkedHashSet<>();
        for (String raw : section.getStringList("members")) {
            try {
                members.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // 잘못된 값은 무시
            }
        }
        return new Team(section.getString("name", displayName), leader, members);
    }

    private String key(String name) {
        return "teams." + name.toLowerCase();
    }

    private void save() {
        try {
            AtomicYaml.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().severe("teams.yml 저장 실패: " + e.getMessage());
        }
    }
}
