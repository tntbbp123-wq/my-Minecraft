package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 플레이어 간 전투(PvP) 태그 상태를 관리한다. 태그된 동안에는 명령어 사용이 막히고, 접속 종료 시 사망 처리된다. */
public class CombatManager {

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, Long> combatUntilMillis = new HashMap<>();

    public CombatManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public int tagSeconds() {
        return plugin.getConfig().getInt("combat.tag-seconds", 15);
    }

    public void tag(UUID uuid) {
        combatUntilMillis.put(uuid, System.currentTimeMillis() + tagSeconds() * 1000L);
    }

    public void untag(UUID uuid) {
        combatUntilMillis.remove(uuid);
    }

    public boolean isTagged(UUID uuid) {
        Long until = combatUntilMillis.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            combatUntilMillis.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = combatUntilMillis.get(uuid);
        if (until == null) {
            return 0;
        }
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }
}
