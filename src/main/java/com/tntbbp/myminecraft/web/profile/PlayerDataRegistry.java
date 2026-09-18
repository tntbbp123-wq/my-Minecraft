package com.tntbbp.myminecraft.web.profile;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 플레이어 상세 섹션 제공자 목록. 등록 순서대로 {@code sections}에 들어간다. */
public class PlayerDataRegistry {

    private final Logger logger;
    private final List<PlayerDataProvider> providers = new CopyOnWriteArrayList<>();

    public PlayerDataRegistry(Logger logger) {
        this.logger = logger;
    }

    /** 제공자를 등록한다. 같은 키가 이미 있으면 새 제공자로 바꾼다. */
    public void register(PlayerDataProvider provider) {
        providers.removeIf(existing -> existing.key().equals(provider.key()));
        providers.add(provider);
    }

    public List<PlayerDataProvider> providers() {
        return List.copyOf(providers);
    }

    /**
     * 모든 섹션을 만든다. <b>메인 스레드에서 호출.</b> 한 제공자가 실패해도 나머지는 채우고,
     * 실패한 섹션은 null로 둔다(경고 로그).
     */
    public JsonObject buildSections(OfflinePlayer player) {
        JsonObject sections = new JsonObject();
        for (PlayerDataProvider provider : providers) {
            try {
                JsonObject section = provider.provide(player);
                sections.add(provider.key(), section == null ? JsonNull.INSTANCE : section);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "플레이어 정보 '" + provider.key() + "' 섹션을 만들지 못했습니다: "
                        + player.getUniqueId(), e);
                sections.add(provider.key(), JsonNull.INSTANCE);
            }
        }
        return sections;
    }
}
