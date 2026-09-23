package com.tntbbp.myminecraft.gui.admin;

import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminItemCategoryTest {

    @Test
    void 모든_카탈로그_분류가_어느_한_칸에_들어간다() {
        for (String id : SpecialItemCatalog.CATEGORY_IDS) {
            assertNotNull(AdminItemCategory.of(id),
                    "'" + id + "' 분류가 관리자 메뉴 어느 칸에도 없으면 그 아이템들이 엉뚱한 칸으로 간다");
        }
    }

    @Test
    void 한_분류가_두_칸에_동시에_들어가지_않는다() {
        for (String id : SpecialItemCatalog.CATEGORY_IDS) {
            long owners = Arrays.stream(AdminItemCategory.values())
                    .filter(category -> category == AdminItemCategory.of(id))
                    .count();
            assertEquals(1, owners, id);
        }
    }

    @Test
    void 요청한_칸이_나뉘어_있다() {
        assertEquals(AdminItemCategory.WEAPON, AdminItemCategory.of("weapon"));
        assertEquals(AdminItemCategory.MATERIAL, AdminItemCategory.of("material"));
        assertEquals(AdminItemCategory.ENHANCE, AdminItemCategory.of("scroll"));
        assertEquals(AdminItemCategory.ENHANCE, AdminItemCategory.of("starforce"));
        assertEquals(AdminItemCategory.COIN, AdminItemCategory.of("coin"));
    }

    @Test
    void 모르는_분류는_null() {
        assertNull(AdminItemCategory.of("없는분류"));
    }

    @Test
    void 첫_화면_분류_칸이_모자라지_않는다() {
        // 분류를 늘리면 첫 화면 칸(CATEGORY_SLOTS)도 같이 늘려야 버튼이 잘리지 않는다.
        int slots = AdminMenuGUI.CATEGORY_SLOTS.length;
        assertEquals(true, AdminItemCategory.values().length <= slots,
                "분류가 " + AdminItemCategory.values().length + "개인데 첫 화면 칸은 " + slots + "개뿐");
    }
}
