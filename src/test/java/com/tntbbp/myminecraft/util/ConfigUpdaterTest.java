package com.tntbbp.myminecraft.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    /** jar 안의 기본 설정을 흉내 낸다. 구조 버전은 실제 파일과 같은 값을 쓴다. */
    private static YamlConfiguration defaults() {
        return yaml("""
                config-version: %d
                economy:
                  starting-balance: 1000.0
                malyongdo:
                  combo-window-seconds: 25
                  second-combo-window-seconds: 40
                materials:
                  list:
                    성스러운태양석:
                      name: "성스러운 태양석"
                      grade: LEGEND
                """.formatted(ConfigUpdater.CURRENT_VERSION));
    }

    // ----- 빠진 항목 채우기 -----

    @Test
    void 없는_항목을_채운다() {
        YamlConfiguration current = yaml("""
                economy:
                  starting-balance: 1000.0
                """);

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertTrue(result.changed());
        assertEquals("성스러운 태양석", current.getString("materials.list.성스러운태양석.name"));
        assertEquals(40, current.getInt("malyongdo.second-combo-window-seconds"));
        // 통째로 새로 생긴 덩어리는 바깥 이름 하나로 묶어서 알린다.
        assertTrue(result.addedBlocks().contains("materials"),
                "실제 값: " + result.addedBlocks());
    }

    @Test
    void 이미_있는_값은_건드리지_않는다() {
        YamlConfiguration current = yaml("""
                config-version: %d
                economy:
                  starting-balance: 999999.0
                """.formatted(ConfigUpdater.CURRENT_VERSION));

        ConfigUpdater.merge(current, defaults());

        assertEquals(999999.0, current.getDouble("economy.starting-balance"));
    }

    @Test
    void 바꿀_것이_없으면_바뀌지_않았다고_한다() {
        YamlConfiguration current = defaults();

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertFalse(result.changed(), "같은 내용인데 바뀌었다고 하면 켤 때마다 파일을 다시 쓴다");
        assertTrue(result.addedBlocks().isEmpty());
    }

    @Test
    void 일부만_있는_구역은_빠진_항목만_채운다() {
        YamlConfiguration current = yaml("""
                config-version: %d
                malyongdo:
                  combo-window-seconds: 12
                """.formatted(ConfigUpdater.CURRENT_VERSION));

        ConfigUpdater.merge(current, defaults());

        assertEquals(12, current.getInt("malyongdo.combo-window-seconds"), "관리자가 정한 값");
        assertEquals(40, current.getInt("malyongdo.second-combo-window-seconds"), "새로 채운 값");
    }

    // ----- 기본값이 바뀐 항목 옮기기 -----

    @Test
    void 옛_기본값_그대로면_새_기본값으로_옮긴다() {
        // config-version이 없는, v1.1.28에서 올라온 서버 파일
        YamlConfiguration current = yaml("""
                malyongdo:
                  combo-window-seconds: 10
                """);

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertEquals(25, current.getInt("malyongdo.combo-window-seconds"));
        assertEquals(1, result.applied().size());
        assertEquals("malyongdo.combo-window-seconds", result.applied().get(0).path());
        assertTrue(result.skipped().isEmpty());
    }

    @Test
    void 직접_고친_값은_옮기지_않는다() {
        YamlConfiguration current = yaml("""
                malyongdo:
                  combo-window-seconds: 8
                """);

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertEquals(8, current.getInt("malyongdo.combo-window-seconds"),
                "관리자가 고른 값을 되돌리면 안 된다");
        assertTrue(result.applied().isEmpty());
        assertEquals(1, result.skipped().size(), "조용히 넘기지 말고 알려야 한다");
    }

    @Test
    void 이미_옮긴_뒤에는_다시_건드리지_않는다() {
        YamlConfiguration current = yaml("""
                config-version: %d
                malyongdo:
                  combo-window-seconds: 25
                """.formatted(ConfigUpdater.CURRENT_VERSION));

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertTrue(result.applied().isEmpty());
        assertTrue(result.skipped().isEmpty(), "이미 끝난 이주를 켤 때마다 알리면 안 된다");
    }

    @Test
    void 옮긴_뒤_구조_버전이_올라간다() {
        YamlConfiguration current = yaml("malyongdo:\n  combo-window-seconds: 10\n");

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertTrue(result.versionBumped());
        assertEquals(ConfigUpdater.CURRENT_VERSION, current.getInt(ConfigUpdater.VERSION_PATH));
        // 구조 버전은 내부 살림이라 "새로 채운 항목"으로 알리지 않는다.
        assertFalse(result.addedBlocks().contains(ConfigUpdater.VERSION_PATH));
    }

    @Test
    void 소수점으로_적힌_옛_기본값도_알아본다() {
        // 관리자가 10을 10.0으로 적어 뒀어도 "옛 기본값 그대로"로 봐야 한다.
        YamlConfiguration current = yaml("malyongdo:\n  combo-window-seconds: 10.0\n");

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertEquals(1, result.applied().size());
        assertEquals(25, current.getInt("malyongdo.combo-window-seconds"));
    }

    @Test
    void 항목_자체가_없으면_새_기본값으로_채우고_이주로_세지_않는다() {
        YamlConfiguration current = yaml("economy:\n  starting-balance: 1000.0\n");

        ConfigUpdater.Result result = ConfigUpdater.merge(current, defaults());

        assertEquals(25, current.getInt("malyongdo.combo-window-seconds"));
        assertTrue(result.applied().isEmpty(), "채운 것이지 옮긴 것이 아니다");
        assertTrue(result.skipped().isEmpty());
    }

    // ----- 설명(주석) -----

    @Test
    void 새로_채운_항목의_설명도_같이_옮긴다() {
        YamlConfiguration defaults = yaml("""
                config-version: %d
                # 전설 등급 모닝스타 '타이탄'
                titan:
                  # 치명타로 들어갈 때의 최종 피해
                  crit-damage: 25.0
                """.formatted(ConfigUpdater.CURRENT_VERSION));
        YamlConfiguration current = yaml("economy:\n  starting-balance: 1000.0\n");

        ConfigUpdater.merge(current, defaults);

        assertEquals(List.of("전설 등급 모닝스타 '타이탄'"), current.getComments("titan"),
                "구역 설명이 빠지면 관리자가 무슨 설정인지 알 수 없다");
        assertEquals(List.of("치명타로 들어갈 때의 최종 피해"),
                current.getComments("titan.crit-damage"));
    }

    @Test
    void 기존_항목의_설명은_건드리지_않는다() {
        YamlConfiguration current = yaml("""
                config-version: %d
                # 우리 서버는 시작 자금을 높게 준다
                economy:
                  starting-balance: 5000.0
                """.formatted(ConfigUpdater.CURRENT_VERSION));

        ConfigUpdater.merge(current, defaults());

        assertEquals(List.of("우리 서버는 시작 자금을 높게 준다"), current.getComments("economy"),
                "관리자가 적어 둔 메모를 기본 설명으로 덮어쓰면 안 된다");
    }

    // ----- 실제 config.yml -----

    @Test
    void 실제_기본설정을_두_번_거쳐도_더_바뀌지_않는다() {
        YamlConfiguration real = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        java.util.Objects.requireNonNull(
                                getClass().getResourceAsStream("/config.yml")),
                        java.nio.charset.StandardCharsets.UTF_8));
        YamlConfiguration copy = yaml(real.saveToString());

        ConfigUpdater.Result first = ConfigUpdater.merge(copy, real);
        assertFalse(first.changed(), "기본 설정 그대로인 파일은 손댈 것이 없어야 한다");

        ConfigUpdater.Result second = ConfigUpdater.merge(copy, real);
        assertFalse(second.changed(), "두 번 돌려도 같아야 한다");
    }

    @Test
    void 실제_기본설정의_구조_버전이_코드와_맞는다() {
        YamlConfiguration real = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(
                        java.util.Objects.requireNonNull(
                                getClass().getResourceAsStream("/config.yml")),
                        java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(ConfigUpdater.CURRENT_VERSION, real.getInt(ConfigUpdater.VERSION_PATH),
                "config.yml의 config-version과 ConfigUpdater.CURRENT_VERSION이 어긋나면"
                        + " 서버를 켤 때마다 파일을 다시 쓴다");
    }

    @Test
    void 목록으로_정의되는_기능이_통째로_살아난다() {
        // v1.1.27 이하에서 올라온 서버: materials 구역이 통째로 없다.
        YamlConfiguration current = yaml("economy:\n  starting-balance: 1000.0\n");

        ConfigUpdater.merge(current, defaults());

        assertEquals(List.of("성스러운태양석"),
                List.copyOf(java.util.Objects.requireNonNull(
                        current.getConfigurationSection("materials.list")).getKeys(false)));
    }

    @Test
    void 맵_목록도_통째로_옮긴다() {
        YamlConfiguration withList = yaml("""
                config-version: %d
                stock:
                  list:
                    - id: GNC
                      price: 100
                """.formatted(ConfigUpdater.CURRENT_VERSION));
        YamlConfiguration current = yaml("economy:\n  starting-balance: 1000.0\n");

        ConfigUpdater.merge(current, withList);

        List<Map<?, ?>> stocks = current.getMapList("stock.list");
        assertEquals(1, stocks.size());
        assertEquals("GNC", stocks.get(0).get("id"));
    }
}
