package com.tntbbp.myminecraft.manager.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.raid.ApocalypseDragon;
import com.tntbbp.myminecraft.raid.EternalKnight;
import com.tntbbp.myminecraft.raid.OblivionEntity;
import com.tntbbp.myminecraft.raid.RaidBoss;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 소환된 레이드 보스들의 생명주기를 관리한다. 보스 본체와 보스가 만든 보조 엔티티
 * (영혼의 파편 등)를 추적해, 리스너가 임의의 엔티티로부터 주인 보스를 찾을 수 있게 한다.
 * 보스 패턴은 여기서 일정 주기로 돌린다.
 */
public class RaidBossManager {

    private static final long TICK_INTERVAL = 10L;

    /** 소환 명령어에서 쓸 수 있는 보스 식별자 목록. */
    public static final List<String> BOSS_IDS =
            List.of(EternalKnight.ID, OblivionEntity.ID, ApocalypseDragon.ID);

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey bossIdKey;
    private final Map<UUID, RaidBoss> bossesByEntityId = new HashMap<>();
    private final Map<UUID, RaidBoss> bossesByAuxId = new HashMap<>();

    /** '사고 정지' 상태인 플레이어와 해제 시각. 보스가 걸고, RaidBossListener가 실제 차단을 맡는다. */
    private final Map<UUID, Long> mindBreakUntilMillis = new HashMap<>();

    private BukkitTask task;

    public RaidBossManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.bossIdKey = new NamespacedKey(plugin, "raid_boss_id");
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        despawnAll();
    }

    private void tick() {
        for (RaidBoss boss : new ArrayList<>(bossesByEntityId.values())) {
            if (!boss.isAlive()) {
                cleanUp(boss);
                continue;
            }
            boss.update();
        }
    }

    /** 알 수 없는 식별자면 null을 반환한다. */
    public RaidBoss spawn(String id, Location location) {
        RaidBoss boss = create(id);
        if (boss == null) {
            return null;
        }
        boss.spawn(location);
        boss.entity().getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, id);
        bossesByEntityId.put(boss.entity().getUniqueId(), boss);
        return boss;
    }

    private RaidBoss create(String id) {
        return switch (id.toLowerCase()) {
            case EternalKnight.ID -> new EternalKnight(plugin);
            case OblivionEntity.ID -> new OblivionEntity(plugin);
            case ApocalypseDragon.ID -> new ApocalypseDragon(plugin);
            default -> null;
        };
    }

    /** 소환하지 않고 표시 이름만 알아낼 때 사용한다 (명령어 안내 등). */
    public String displayNameOf(String id) {
        RaidBoss boss = create(id);
        return boss == null ? null : boss.displayName();
    }

    /**
     * 엔더드래곤은 머리/날개/꼬리가 각각 별도의 파트 엔티티라 피격 이벤트가 파트로 올 수 있다.
     * 그럴 때는 본체로 바꿔서 찾는다.
     */
    public RaidBoss byEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return bossesByEntityId.get(entity.getUniqueId());
    }

    public RaidBoss byAuxEntity(Entity entity) {
        return entity == null ? null : bossesByAuxId.get(entity.getUniqueId());
    }

    public void registerAuxEntity(RaidBoss boss, Entity aux) {
        bossesByAuxId.put(aux.getUniqueId(), boss);
    }

    public void unregisterAuxEntity(Entity aux) {
        bossesByAuxId.remove(aux.getUniqueId());
    }

    /** 보스가 죽었거나 사라졌을 때 추적 목록과 보스바를 정리한다. */
    public void cleanUp(RaidBoss boss) {
        bossesByEntityId.remove(boss.entity().getUniqueId());
        bossesByAuxId.values().removeIf(owner -> owner == boss);
        boss.remove(false);
    }

    /** 서버 종료/명령어로 살아있는 보스를 전부 제거한다. */
    public int despawnAll() {
        List<RaidBoss> bosses = new ArrayList<>(bossesByEntityId.values());
        for (RaidBoss boss : bosses) {
            boss.remove(true);
        }
        bossesByEntityId.clear();
        bossesByAuxId.clear();
        mindBreakUntilMillis.clear();
        return bosses.size();
    }

    // ----- 사고 정지 (기억할 수 없는 자) -----

    /** 지정한 시간(초) 동안 이동·명령어·채팅·스킬 사용을 막는다. */
    public void applyMindBreak(Player player, int seconds) {
        mindBreakUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isMindBroken(UUID uuid) {
        Long until = mindBreakUntilMillis.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            mindBreakUntilMillis.remove(uuid);
            return false;
        }
        return true;
    }

    public int activeCount() {
        return bossesByEntityId.size();
    }

    public List<RaidBoss> activeBosses() {
        return new ArrayList<>(bossesByEntityId.values());
    }
}
