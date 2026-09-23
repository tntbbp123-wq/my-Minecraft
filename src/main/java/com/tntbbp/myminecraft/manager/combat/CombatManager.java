package com.tntbbp.myminecraft.manager.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
        return plugin.getConfig().getInt("combat.tag-seconds", 45);
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

    /**
     * 전투 중이면 순간이동을 막고 알린다. 막았으면 true.
     *
     * <p>전투 태그는 원래 <b>명령어만</b> 막았다. 그런데 메뉴의 스폰·랜덤 이동 버튼, 홈 GUI, 그리고 다른 사람이
     * 수락하는 {@code /tpa}는 명령어를 거치지 않아서, 전투 전에 메뉴를 열어 두거나 팀원에게 미리 tpa를 보내 두면
     * 전투 중에 그대로 빠져나갈 수 있었다. 순간이동하는 모든 곳에서 이 검사를 부른다.
     * 명령어 막기와 같게 관리자({@code myminecraft.admin})는 막지 않는다.
     */
    public boolean blockTeleportIfTagged(Player player) {
        if (player.hasPermission("myminecraft.admin") || !isTagged(player.getUniqueId())) {
            return false;
        }
        player.sendMessage(ChatColor.RED + "전투 중에는 이동할 수 없습니다. ("
                + remainingSeconds(player.getUniqueId()) + "초 후 가능)");
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
