package com.tntbbp.myminecraft.manager.social;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 플레이어 UUID <-> 디스코드 사용자 ID 연동을 관리한다 (침입 알림 DM 대상 확인용).
 * /디스코드연동 <디스코드ID> 로 인증 코드를 DM으로 받고, /디스코드연동확인 <코드> 로 완료한다.
 */
public class DiscordLinkManager {

    private record PendingVerification(String discordId, String code, long expiresAt) {
    }

    public enum ConfirmResult {
        SUCCESS,
        NO_PENDING,
        EXPIRED,
        WRONG_CODE
    }

    private final MyMinecraftPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, PendingVerification> pending = new HashMap<>();

    public DiscordLinkManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discord-links.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("discord-links.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public String getLinkedDiscordId(UUID uuid) {
        return data.getString(uuid.toString());
    }

    public boolean isLinked(UUID uuid) {
        return getLinkedDiscordId(uuid) != null;
    }

    public int verificationExpirySeconds() {
        return plugin.getConfig().getInt("discord.verification-expiry-seconds", 300);
    }

    /** 인증 코드를 발급해 대기 목록에 저장하고 반환한다 (호출부에서 DM으로 전송). */
    public String startVerification(UUID uuid, String discordId) {
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        pending.put(uuid, new PendingVerification(discordId, code,
                System.currentTimeMillis() + verificationExpirySeconds() * 1000L));
        return code;
    }

    public ConfirmResult confirm(UUID uuid, String code) {
        PendingVerification verification = pending.get(uuid);
        if (verification == null) {
            return ConfirmResult.NO_PENDING;
        }
        if (System.currentTimeMillis() > verification.expiresAt()) {
            pending.remove(uuid);
            return ConfirmResult.EXPIRED;
        }
        if (!verification.code().equals(code)) {
            return ConfirmResult.WRONG_CODE;
        }
        pending.remove(uuid);
        data.set(uuid.toString(), verification.discordId());
        save();
        return ConfirmResult.SUCCESS;
    }

    public void unlink(UUID uuid) {
        data.set(uuid.toString(), null);
        pending.remove(uuid);
        save();
    }

    private void save() {
        try {
            AtomicYaml.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().severe("discord-links.yml 저장 실패: " + e.getMessage());
        }
    }
}
