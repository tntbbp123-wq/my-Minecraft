package com.tntbbp.myminecraft.web.profile;

import com.google.gson.JsonObject;
import org.bukkit.OfflinePlayer;

/**
 * 플레이어 상세({@code GET /players/{uuid}/profile})의 섹션 하나를 만든다.
 * 직업·레벨 같은 새 RPG 데이터가 생기면 제공자를 하나 만들어 {@link PlayerDataRegistry}에 등록하기만 하면 된다.
 * 메인 스레드에서 호출된다. 오프라인 플레이어도 들어오므로 UUID 키 저장소에서 읽고,
 * 온라인 전용 값은 오프라인이면 null로 둔다.
 */
public interface PlayerDataProvider {

    /** {@code sections} 안의 키 (예: {@code "economy"}). */
    String key();

    /** 섹션 내용. 해당 없음이면 null(→ JSON null). */
    JsonObject provide(OfflinePlayer player);
}
