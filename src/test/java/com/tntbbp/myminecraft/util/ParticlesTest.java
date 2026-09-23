package com.tntbbp.myminecraft.util;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParticlesTest {

    @Test
    void 색이_필요하면_흰색을_채운다() {
        // Paper 1.21.10부터 FLASH가 색을 요구한다
        assertEquals(Color.WHITE, Particles.defaultData(Color.class));
    }

    @Test
    void 세기가_필요하면_1을_채운다() {
        // Paper 1.21.10부터 DRAGON_BREATH가 세기(Float)를 요구한다
        assertEquals(1.0f, Particles.defaultData(Float.class));
    }

    @Test
    void 모르는_데이터는_채우지_않는다() {
        // 엉뚱한 값을 넣어 또 터지느니 그 연출만 건너뛰는 편이 낫다
        assertNull(Particles.defaultData(String.class));
    }
}
