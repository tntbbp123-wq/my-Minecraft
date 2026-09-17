package com.tntbbp.myminecraft.manager.social;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

/**
 * 디스코드 봇으로 플레이어 개인에게 DM 알림을 보내는 관리자.
 * config.yml의 discord.bot-token이 비어있으면 봇이 아예 시작되지 않고, 이 기능만 조용히
 * 비활성화된다 (나머지 플러그인 기능에는 영향 없음).
 */
public class DiscordManager {

    private final MyMinecraftPlugin plugin;
    private JDA jda;

    public DiscordManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return jda != null;
    }

    public void start() {
        // 설정값은 평문일 수도 있고 env:/file:/enc: 로 감춰둔 형태일 수도 있다 (SecretResolver 참고).
        String token = plugin.getSecretResolver().resolve(plugin.getConfig().getString("discord.bot-token", ""));
        if (token.isBlank()) {
            plugin.getLogger().info("discord.bot-token이 비어있어 디스코드 알림 기능은 비활성화됩니다.");
            return;
        }
        try {
            this.jda = JDABuilder.createLight(token).build();
            plugin.getLogger().info("디스코드 봇이 시작되었습니다. 침입 알림 DM 기능이 활성화됩니다.");
        } catch (Exception e) {
            plugin.getLogger().severe("디스코드 봇 시작 실패 (토큰을 확인하세요): " + e.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    /** 지정한 디스코드 사용자 ID에게 DM을 보낸다. 실패(차단/미공유 서버 등)하면 onFailure가 호출된다. */
    public void sendDirectMessage(String discordUserId, String message, Runnable onFailure) {
        if (jda == null) {
            runFailure(onFailure);
            return;
        }
        jda.retrieveUserById(discordUserId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessage(message).queue(null, failure -> runFailure(onFailure)),
                        failure -> runFailure(onFailure)),
                failure -> runFailure(onFailure));
    }

    private void runFailure(Runnable onFailure) {
        if (onFailure != null) {
            onFailure.run();
        }
    }
}
