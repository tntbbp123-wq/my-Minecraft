package com.tntbbp.myminecraft.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * OP 플레이어는 이 플러그인이 거는 모든 부정적 효과(화상/감전/함정/연막/화염병/전투로그 강제사망/
 * 방어관통으로 인한 피해 증가 등)에 영향받지 않는다.
 */
public final class OpImmunity {

    private OpImmunity() {
    }

    public static boolean isImmune(Entity entity) {
        return entity instanceof Player player && player.isOp();
    }
}
