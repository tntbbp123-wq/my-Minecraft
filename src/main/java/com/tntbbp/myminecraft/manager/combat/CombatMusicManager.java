package com.tntbbp.myminecraft.manager.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * PvP 전투 태그가 걸려 있는 동안 전투 음악을 재생한다.
 *
 * <p>{@link Sound.Emitter#self()}로 비위치 재생하므로 소리가 플레이어를 따라다닌다. 좌표에
 * 재생하면 거리에 따라 감쇠해서 도망치는 순간 끊기는데, 그걸 피하기 위해서다.
 *
 * <p>음원은 리소스팩에 들어 있어야 한다. 저작권 때문에 저장소에 포함하지 않으므로 기본값은
 * 꺼짐이고, 서버 운영자가 음원을 넣은 뒤 config에서 켜야 한다 (README 참고). 리소스팩을 적용하지
 * 않은 플레이어에게는 아무 소리도 나지 않을 뿐 다른 영향은 없다.
 */
public class CombatMusicManager {

    private static final long TICK_INTERVAL = 20L;

    private final MyMinecraftPlugin plugin;
    private final CombatManager combatManager;

    /** 플레이어별로 현재 트랙을 재생하기 시작한 시각. 트랙이 끝나면 다시 틀기 위해 쓴다. */
    private final Map<UUID, Long> playingSinceMillis = new HashMap<>();

    private BukkitTask task;

    public CombatMusicManager(MyMinecraftPlugin plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("combat.music.enabled", false);
    }

    private String soundKey() {
        return plugin.getConfig().getString("combat.music.sound", "myminecraft:music.combat");
    }

    /** 트랙 길이(초). 전투가 이보다 길어지면 다시 처음부터 틀어 끊기지 않게 한다. */
    private int trackLengthSeconds() {
        return plugin.getConfig().getInt("combat.music.track-length-seconds", 142);
    }

    private float volume() {
        return (float) plugin.getConfig().getDouble("combat.music.volume", 0.7);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        stopAll();
    }

    private void tick() {
        if (!isEnabled()) {
            if (!playingSinceMillis.isEmpty()) {
                stopAll();
            }
            return;
        }

        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!combatManager.isTagged(uuid)) {
                if (playingSinceMillis.remove(uuid) != null) {
                    stopFor(player);
                }
                continue;
            }

            Long since = playingSinceMillis.get(uuid);
            if (since == null || now - since >= trackLengthSeconds() * 1000L) {
                playFor(player);
                playingSinceMillis.put(uuid, now);
            }
        }

        // 접속을 끊은 플레이어의 기록은 남겨둘 필요가 없다.
        Iterator<UUID> iterator = playingSinceMillis.keySet().iterator();
        while (iterator.hasNext()) {
            Player player = plugin.getServer().getPlayer(iterator.next());
            if (player == null || !player.isOnline()) {
                iterator.remove();
            }
        }
    }

    private void playFor(Player player) {
        player.playSound(Sound.sound(key(), Sound.Source.MUSIC, volume(), 1.0f), Sound.Emitter.self());
    }

    /** 전투가 끝났거나 사망했을 때 즉시 음악을 끊는다. */
    public void stopFor(Player player) {
        player.stopSound(SoundStop.named(key()));
    }

    public void stopAll() {
        for (UUID uuid : playingSinceMillis.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                stopFor(player);
            }
        }
        playingSinceMillis.clear();
    }

    /** 전투 태그가 풀리는 즉시(사망/퇴장 등) 호출해서 다음 틱을 기다리지 않게 한다. */
    public void clear(Player player) {
        playingSinceMillis.remove(player.getUniqueId());
        stopFor(player);
    }

    private Key key() {
        return Key.key(soundKey());
    }
}
