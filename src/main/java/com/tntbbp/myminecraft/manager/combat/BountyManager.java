package com.tntbbp.myminecraft.manager.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.AtomicYaml;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 현상금 표식(Mark of Bounty) — 플레이어끼리 서로에게 찍는 개인 표식.
 *
 * <p>규칙은 단순하다. <b>한 사람이 가진 표식은 하나뿐이다.</b> 다른 사람에게 옮겨 찍을 수는 있지만
 * 동시에 두 명을 찍을 수는 없다. 그래서 정말 위협적인 상대에게 신중하게 쓰게 된다.
 *
 * <p>표식을 <b>받은</b> 쪽은 리스크와 보상을 같이 진다.
 * <ul>
 *   <li>받는 피해가 표식 1개당 {@code damage-taken-percent-per-mark}(기본 0.4%)씩 늘어난다</li>
 *   <li>주는 피해가 표식 1개당 {@code damage-dealt-percent-per-mark}(기본 0.5%)씩 늘어난다</li>
 * </ul>
 *
 * <p>여러 명에게 찍힐수록 맞을 때 아프지만 반격도 세진다. 1:다수 상황을 뒤집을 여지를 남기려는 것이다.
 *
 * <p>OP에게는 양쪽 모두 적용하지 않는다. 받는 피해 증가만 빼면 OP가 이득만 챙기게 되므로, 아예 표식
 * 효과 밖에 둔다. 표식을 찍는 것 자체는 막지 않는다.
 *
 * <p>저장은 {@code bounty.yml}. 키가 찍은 사람, 값이 찍힌 사람이다 (1인 1표식이라 이 방향이 자연스럽다).
 */
public class BountyManager {

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    /** 찍은 사람 -> 찍힌 사람. */
    private final Map<UUID, UUID> marks = new LinkedHashMap<>();
    /** 찍은 사람 -> 마지막으로 표식을 옮긴 시각. */
    private final Map<UUID, Long> lastChanged = new HashMap<>();

    public BountyManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounty.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("bounty.yml을 만들지 못했습니다: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    // ----- 설정 -----

    public double damageTakenPercentPerMark() {
        return plugin.getConfig().getDouble("bounty.damage-taken-percent-per-mark", 0.4);
    }

    public double damageDealtPercentPerMark() {
        return plugin.getConfig().getDouble("bounty.damage-dealt-percent-per-mark", 0.5);
    }

    /** 표식을 옮기고 나서 다시 옮길 수 있을 때까지의 시간. 0이면 제한 없음. */
    public int changeCooldownSeconds() {
        return plugin.getConfig().getInt("bounty.change-cooldown-seconds", 3600);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bounty.enabled", true);
    }

    // ----- 조회 -----

    /** 이 사람이 지금 누구를 찍어뒀는지. 안 찍었으면 null. */
    public UUID markedBy(UUID voter) {
        return marks.get(voter);
    }

    /** 이 사람이 받은 표식 개수. */
    public int markCount(UUID target) {
        int count = 0;
        for (UUID marked : marks.values()) {
            if (marked.equals(target)) {
                count++;
            }
        }
        return count;
    }

    /** 표식을 많이 받은 순서. 표식이 0개인 사람은 들어가지 않는다. */
    public List<Map.Entry<UUID, Integer>> ranking(int limit) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID marked : marks.values()) {
            counts.merge(marked, 1, Integer::sum);
        }
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        return sorted.size() > limit ? new ArrayList<>(sorted.subList(0, limit)) : sorted;
    }

    /** 표식을 다시 옮길 수 있을 때까지 남은 시간(초). 0이면 지금 옮길 수 있다. */
    public long remainingChangeCooldown(UUID voter) {
        int cooldown = changeCooldownSeconds();
        if (cooldown <= 0) {
            return 0;
        }
        Long last = lastChanged.get(voter);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldown - (System.currentTimeMillis() - last) / 1000);
    }

    // ----- 표식 찍기 / 거두기 -----

    /** 표식 부착 결과. */
    public enum MarkResult {
        SUCCESS,
        /** 자기 자신은 찍을 수 없다. */
        SELF,
        /** 이미 같은 사람을 찍어뒀다. */
        SAME_TARGET,
        /** 아직 옮길 수 없다. */
        COOLDOWN,
        /** 기능이 꺼져 있다. */
        DISABLED
    }

    /**
     * 표식을 찍는다. 이미 다른 사람을 찍어뒀다면 그쪽에서 떼어 이쪽으로 옮긴다
     * (1인 1표식이라 옮기는 것 외에 늘릴 방법이 없다).
     */
    public MarkResult mark(UUID voter, UUID target) {
        if (!enabled()) {
            return MarkResult.DISABLED;
        }
        if (voter.equals(target)) {
            return MarkResult.SELF;
        }
        if (target.equals(marks.get(voter))) {
            return MarkResult.SAME_TARGET;
        }
        if (remainingChangeCooldown(voter) > 0) {
            return MarkResult.COOLDOWN;
        }
        marks.put(voter, target);
        lastChanged.put(voter, System.currentTimeMillis());
        save();
        return MarkResult.SUCCESS;
    }

    /** 찍어둔 표식을 거둔다. 거둘 표식이 없으면 false. */
    public boolean unmark(UUID voter) {
        if (marks.remove(voter) == null) {
            return false;
        }
        lastChanged.put(voter, System.currentTimeMillis());
        save();
        return true;
    }

    // ----- 피해 보정 -----

    /** 이 사람이 <b>받는</b> 피해 배율. 표식이 없거나 OP면 1.0. */
    public double damageTakenMultiplier(Player victim) {
        if (!enabled() || OpImmunity.isImmune(victim)) {
            return 1.0;
        }
        return 1.0 + markCount(victim.getUniqueId()) * damageTakenPercentPerMark() / 100.0;
    }

    /** 이 사람이 <b>주는</b> 피해 배율. 표식이 없거나 OP면 1.0. */
    public double damageDealtMultiplier(Player attacker) {
        if (!enabled() || OpImmunity.isImmune(attacker)) {
            return 1.0;
        }
        return 1.0 + markCount(attacker.getUniqueId()) * damageDealtPercentPerMark() / 100.0;
    }

    // ----- 저장 -----

    private void load() {
        marks.clear();
        lastChanged.clear();
        if (data.getConfigurationSection("marks") == null) {
            return;
        }
        for (String key : data.getConfigurationSection("marks").getKeys(false)) {
            try {
                UUID voter = UUID.fromString(key);
                String targetId = data.getString("marks." + key + ".target");
                if (targetId == null) {
                    continue;
                }
                marks.put(voter, UUID.fromString(targetId));
                long changed = data.getLong("marks." + key + ".changed-at", 0L);
                if (changed > 0) {
                    lastChanged.put(voter, changed);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("bounty.yml에 잘못된 UUID가 있습니다: " + key);
            }
        }
    }

    private void save() {
        data.set("marks", null);
        for (Map.Entry<UUID, UUID> entry : marks.entrySet()) {
            String key = "marks." + entry.getKey();
            data.set(key + ".target", entry.getValue().toString());
            Long changed = lastChanged.get(entry.getKey());
            if (changed != null) {
                data.set(key + ".changed-at", changed);
            }
        }
        try {
            AtomicYaml.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().warning("bounty.yml을 저장하지 못했습니다: " + e.getMessage());
        }
    }
}
