package com.tntbbp.myminecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 버전이 달라도 터지지 않는 파티클 호출.
 *
 * <p>이 플러그인은 Paper 1.21.1 API로 빌드하지만 서버는 더 새 1.21.x에서 돌 수 있다. 그 사이 몇몇
 * 파티클이 <b>추가 데이터를 필수로 요구</b>하게 바뀌었다 (Paper 1.21.10 기준 {@code FLASH}는 색,
 * {@code DRAGON_BREATH}는 세기). 옛 방식대로 데이터 없이 부르면 {@code IllegalArgumentException}이 나고,
 * 그 줄 <b>뒤에 있던 피해·띄우기 처리까지 통째로 건너뛴다.</b> 연출 한 줄 때문에 기술이 헛방이 된다.
 *
 * <p>그래서 서버가 <b>실제로 요구하는 데이터 종류를 실행 중에 확인</b>해서 필요할 때만 기본값을 채워 넣는다.
 * 그래도 실패하면 연출만 건너뛰고 기술은 계속 진행한다. 조용히 삼키면 다음에 원인을 못 찾으니,
 * 파티클 종류마다 처음 한 번은 콘솔에 남긴다.
 */
public final class Particles {

    private static final Set<Particle> WARNED = ConcurrentHashMap.newKeySet();

    private Particles() {
    }

    /** 번쩍임. 1.21.10부터는 색이 필수라 흰색을 넣는다. */
    public static void flash(World world, Location at, int count) {
        spawn(world, Particle.FLASH, at, count, 0, 0, 0, 0);
    }

    /**
     * 데이터가 따로 필요 없는 모양의 파티클을 뿌린다. 서버가 데이터를 요구하면 알맞은 기본값을 채운다.
     * 이 메서드는 <b>절대 예외를 밖으로 던지지 않는다.</b>
     */
    public static void spawn(World world, Particle particle, Location at, int count,
                             double offsetX, double offsetY, double offsetZ, double speed) {
        Object data = null;
        Class<?> required = particle.getDataType();
        if (required != Void.class) {
            data = defaultData(required);
            if (data == null) {
                warnOnce(particle, "서버가 " + required.getSimpleName() + " 데이터를 요구하는데 기본값을 모름");
                return;
            }
        }
        try {
            world.spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed, data);
        } catch (IllegalArgumentException e) {
            warnOnce(particle, e.getMessage());
        }
    }

    /** 새 버전에서 필수가 된 데이터의 무난한 기본값. 모르는 종류면 null. */
    static Object defaultData(Class<?> required) {
        if (required == Color.class) {
            return Color.WHITE;
        }
        if (required == Float.class) {
            return 1.0f;
        }
        return null;
    }

    private static void warnOnce(Particle particle, String reason) {
        if (WARNED.add(particle)) {
            Bukkit.getLogger().warning("[MyMinecraft] 파티클 " + particle + " 연출을 건너뜁니다 (" + reason
                    + "). 기술 효과는 그대로 적용됩니다.");
        }
    }
}
