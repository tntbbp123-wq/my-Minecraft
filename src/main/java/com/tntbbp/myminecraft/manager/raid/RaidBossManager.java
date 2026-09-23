package com.tntbbp.myminecraft.manager.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.raid.ApocalypseDragon;
import com.tntbbp.myminecraft.raid.EternalKnight;
import com.tntbbp.myminecraft.raid.OblivionEntity;
import com.tntbbp.myminecraft.raid.RaidBoss;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
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
    private final NamespacedKey fragmentKey;
    private final Map<UUID, RaidBoss> bossesByEntityId = new HashMap<>();
    private final Map<UUID, RaidBoss> bossesByAuxId = new HashMap<>();

    /** '사고 정지' 상태인 플레이어와 해제 시각. 보스가 걸고, RaidBossListener가 실제 차단을 맡는다. */
    private final Map<UUID, Long> mindBreakUntilMillis = new HashMap<>();

    private BukkitTask task;

    public RaidBossManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.bossIdKey = new NamespacedKey(plugin, "raid_boss_id");
        this.fragmentKey = new NamespacedKey(plugin, EternalKnight.FRAGMENT_KEY);
    }

    public void start() {
        for (World world : plugin.getServer().getWorlds()) {
            removeOrphans(world.getEntities());
        }
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

    /**
     * 추적하지 않는데 월드에 남아 있는 보스·보조 엔티티를 치운다. 치운 수를 돌려준다.
     *
     * <p>보스는 월드에 저장되게({@code setPersistent(true)}) 소환된다. 서버가 정상 종료되면 {@link #despawnAll}이
     * 치우지만, 서버가 튕기거나 종료할 때 보스가 있던 청크가 이미 내려가 있으면 그대로 남는다. 다시 켜면 그
     * 보스는 추적 목록에 없어 기술·보스바·보상이 전부 없는 체력 덩어리가 되고(종말룡은 진짜 엔더드래곤이라
     * 지상을 날아다닌다), 잡아도 아무것도 주지 않았다.
     */
    public int removeOrphans(Iterable<? extends Entity> entities) {
        int removed = 0;
        for (Entity entity : entities) {
            PersistentDataContainer data = entity.getPersistentDataContainer();
            boolean orphanBoss = data.has(bossIdKey, PersistentDataType.STRING)
                    && !bossesByEntityId.containsKey(entity.getUniqueId());
            boolean orphanAux = data.has(fragmentKey, PersistentDataType.BYTE)
                    && !bossesByAuxId.containsKey(entity.getUniqueId());
            if (orphanBoss || orphanAux) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("추적하지 않는 레이드 보스 잔재 " + removed + "개를 치웠습니다.");
        }
        return removed;
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
