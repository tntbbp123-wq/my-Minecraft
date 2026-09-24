package com.tntbbp.myminecraft;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 웹 관리 설정 카탈로그({@code admin-settings-catalog.yml})가 {@code config.yml}과 맞는지 검사한다(CLAUDE.md 7절).
 *
 * <p>키 단위는 관리 사이트 화면과 같다: 맵은 점 경로로 펼치고(키 안의 {@code .}·{@code \}는 {@code \}로 이스케이프),
 * 목록·빈 맵·글자·숫자는 한 값(말단 키)으로 둔다.
 *
 * <ul>
 *   <li>항상 검사: 카탈로그 형식, 같은 키 두 번, config.yml에 없는 키(이름이 바뀌었거나 지운 키),
 *       비밀 같은 키의 {@code secret: true}, 묶음 값의 {@code editable: false}.</li>
 *   <li>{@link #everyConfigKeyHasCatalogEntry}: config.yml의 모든 말단 키가 카탈로그에 있는지.
 *       카탈로그를 다 채웠으므로 기본으로 강제한다({@link #ENFORCE_COVERAGE_BY_DEFAULT}) — config.yml 에 키만 추가하고
 *       카탈로그를 안 고치면 평소 빌드가 실패한다. 잠깐 목록만 보고 싶으면 {@code -Dgn.catalog.strict=false}.</li>
 * </ul>
 */
class AdminSettingsCatalogTest {

    /** 카탈로그를 다 채워서 true(기본 강제). config.yml 에 키만 추가하고 카탈로그를 안 고치면 빌드가 실패한다. */
    private static final boolean ENFORCE_COVERAGE_BY_DEFAULT = true;

    private static final String FILE_ID = "plugin:MyMinecraft/config.yml";
    private static final Set<String> TYPES = Set.of("string", "int", "float", "bool", "enum", "list", "secret");
    private static final Set<String> RISKS = Set.of("low", "medium", "high");
    private static final Set<String> LIVE_TYPES = Set.of("enum", "bool", "int", "float");
    private static final Set<String> BOOL_FIELDS = Set.of("restart", "secret", "verify", "editable");
    /** gn-admin deploy/gn-settings/secret-policy.yaml 의 key_patterns 와 같은 규칙(경로 중 한 조각이라도 맞으면 비밀). */
    private static final Pattern SECRET_SEGMENT = Pattern.compile(
            ".*(token|password|passwd|secret|api-key|api_key|apikey|webhook|private-key|credential|license|auth-string).*",
            Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- 항상 검사

    @Test
    void catalogHasExpectedShape() {
        Map<String, Object> root = loadCatalogRoot();
        assertEquals(1, root.get("version"), "version 은 1 이어야 해요");
        Map<String, Object> file = catalogFile(root);
        assertEquals("plugins/MyMinecraft/config.yml", file.get("path"));
        assertEquals("yaml", file.get("format"));
        assertEquals("plugin", file.get("kind"));
        assertTrue(file.get("keys") instanceof List<?>, "keys 는 목록이어야 해요");
    }

    @Test
    void catalogEntriesAreWellFormed() {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> entry : catalogEntries(problems)) {
            Object keyObj = entry.get("key");
            if (!(keyObj instanceof String key) || key.isBlank()) {
                problems.add("key 가 없거나 글자가 아닌 항목: " + entry);
                continue;
            }
            if (!seen.add(key)) {
                problems.add(key + ": 같은 key 가 두 번 있어요");
            }
            if (!(entry.get("label") instanceof String label) || label.isBlank()) {
                problems.add(key + ": label(한국어 이름)이 없어요");
            }
            if (!(entry.get("desc") instanceof String desc) || desc.isBlank()) {
                problems.add(key + ": desc(한국어 설명)가 없어요");
            }
            Object type = entry.getOrDefault("type", "string");
            if (!TYPES.contains(String.valueOf(type))) {
                problems.add(key + ": type 은 " + TYPES + " 중 하나여야 해요 (지금: " + type + ")");
            }
            if ("enum".equals(type) && !(entry.get("values") instanceof List<?> values && !values.isEmpty())) {
                problems.add(key + ": enum 은 values 목록이 필요해요");
            }
            Object risk = entry.getOrDefault("risk", "low");
            if (!RISKS.contains(String.valueOf(risk))) {
                problems.add(key + ": risk 는 low·medium·high 중 하나여야 해요");
            }
            for (String field : BOOL_FIELDS) {
                if (entry.containsKey(field) && !(entry.get(field) instanceof Boolean)) {
                    problems.add(key + ": " + field + " 는 true/false 여야 해요");
                }
            }
            if (entry.get("default") instanceof Map<?, ?>) {
                problems.add(key + ": default 는 글자·숫자·목록만 쓸 수 있어요(맵이면 default 를 빼세요)");
            }
            Object live = entry.get("live_command");
            if (live != null && !"".equals(live)) {
                boolean restart = !Boolean.FALSE.equals(entry.get("restart"));
                boolean secret = Boolean.TRUE.equals(entry.get("secret")) || "secret".equals(type);
                if (restart || secret || !LIVE_TYPES.contains(String.valueOf(type))) {
                    problems.add(key + ": live_command 는 restart: false 인 enum·bool·int·float 항목에만 쓸 수 있어요");
                }
            }
        }
        assertNoProblems("카탈로그 형식 오류", problems);
    }

    @Test
    void catalogKeysExistInConfig() {
        Map<String, Object> leaves = configLeaves();
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> entry : catalogEntries(problems)) {
            Object key = entry.get("key");
            if (key instanceof String k && !leaves.containsKey(k)) {
                problems.add(k + ": config.yml 에 이 말단 키가 없어요(이름이 바뀌었거나 지운 키, 또는 맵 전체를 가리킨 키)");
            }
        }
        assertNoProblems("config.yml 에 없는 카탈로그 키", problems);
    }

    @Test
    void secretLikeKeysAreMarkedSecret() {
        Map<String, Object> leaves = configLeaves();
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> entry : catalogEntries(problems)) {
            if (!(entry.get("key") instanceof String key)) {
                continue;
            }
            boolean secret = Boolean.TRUE.equals(entry.get("secret")) || "secret".equals(entry.get("type"));
            boolean looksSecret = splitPath(key).stream().anyMatch(seg -> SECRET_SEGMENT.matcher(seg).matches())
                    || (leaves.get(key) instanceof String value && value.startsWith("env:"));
            if (looksSecret && !secret) {
                problems.add(key + ": 비밀값(토큰·비밀번호·API 키·env: 값)은 secret: true 로 적어야 해요");
            }
        }
        assertNoProblems("비밀 표시 누락", problems);
    }

    @Test
    void bundleValuesAreReadOnly() {
        Map<String, Object> leaves = configLeaves();
        List<String> problems = new ArrayList<>();
        for (Map<String, Object> entry : catalogEntries(problems)) {
            if (!(entry.get("key") instanceof String key) || !leaves.containsKey(key)) {
                continue;
            }
            if (isBundle(leaves.get(key)) && !Boolean.FALSE.equals(entry.get("editable"))) {
                problems.add(key + ": 맵·객체 목록 같은 묶음 값은 editable: false 로 적어야 해요");
            }
        }
        assertNoProblems("묶음 값 editable 누락", problems);
    }

    // ---------------------------------------------------------------- 채우기 검사(기본 강제, -Dgn.catalog.strict=false 면 목록만 출력)

    @Test
    void everyConfigKeyHasCatalogEntry() {
        Set<String> catalogKeys = new HashSet<>();
        for (Map<String, Object> entry : catalogEntries(new ArrayList<>())) {
            if (entry.get("key") instanceof String key) {
                catalogKeys.add(key);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : configLeaves().keySet()) {
            if (!catalogKeys.contains(key)) {
                missing.add(key);
            }
        }
        boolean strict = Boolean.parseBoolean(
                System.getProperty("gn.catalog.strict", String.valueOf(ENFORCE_COVERAGE_BY_DEFAULT)));
        String report = "admin-settings-catalog.yml 에 없는 config.yml 키 " + missing.size() + "개:\n  "
                + String.join("\n  ", missing);
        if (missing.isEmpty()) {
            return;
        }
        if (!strict) {
            System.out.println("[설정 카탈로그] " + report);
            Assumptions.abort("카탈로그 채우기 검사를 -Dgn.catalog.strict=false 로 껐어요. 빠진 키 "
                    + missing.size() + "개");
        }
        throw new AssertionError(report);
    }

    // ---------------------------------------------------------------- 도우미

    private static void assertNoProblems(String title, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new AssertionError(title + " " + problems.size() + "건:\n  " + String.join("\n  ", problems));
        }
    }

    private static boolean isBundle(Object value) {
        if (value instanceof Map<?, ?>) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(v -> v instanceof Map<?, ?> || v instanceof List<?>);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadCatalogRoot() {
        Object root = new Yaml().load(readResource("admin-settings-catalog.yml"));
        assertTrue(root instanceof Map<?, ?>, "카탈로그 최상위는 객체(version, files)여야 해요");
        return (Map<String, Object>) root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> catalogFile(Map<String, Object> root) {
        Object files = root.get("files");
        assertTrue(files instanceof List<?>, "files 는 목록이어야 해요");
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object file : (List<?>) files) {
            if (file instanceof Map<?, ?> map && FILE_ID.equals(map.get("id"))) {
                matches.add((Map<String, Object>) map);
            }
        }
        assertEquals(1, matches.size(), "files 에 id \"" + FILE_ID + "\" 항목이 딱 하나 있어야 해요");
        return matches.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> catalogEntries(List<String> problems) {
        Object keys = catalogFile(loadCatalogRoot()).get("keys");
        List<Map<String, Object>> out = new ArrayList<>();
        if (keys instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                } else {
                    problems.add("keys 의 항목은 객체여야 해요: " + entry);
                }
            }
        }
        return out;
    }

    /** config.yml 의 말단 키 → 값 (화면과 같은 펼치기 규칙). */
    private static Map<String, Object> configLeaves() {
        Object root = new Yaml().load(readResource("config.yml"));
        assertTrue(root instanceof Map<?, ?>, "config.yml 최상위는 맵이어야 해요");
        Map<String, Object> out = new LinkedHashMap<>();
        flatten((Map<?, ?>) root, "", out);
        return out;
    }

    private static void flatten(Map<?, ?> node, String prefix, Map<String, Object> out) {
        for (Map.Entry<?, ?> e : node.entrySet()) {
            String path = prefix.isEmpty() ? escape(keyStr(e.getKey())) : prefix + "." + escape(keyStr(e.getKey()));
            if (e.getValue() instanceof Map<?, ?> child && !child.isEmpty()) {
                flatten(child, path, out);
            } else {
                out.put(path, e.getValue());
            }
        }
    }

    private static String keyStr(Object key) {
        return key == null ? "null" : String.valueOf(key);
    }

    private static String escape(String segment) {
        return segment.replace("\\", "\\\\").replace(".", "\\.");
    }

    private static List<String> splitPath(String path) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length()) {
                cur.append(path.charAt(++i));
            } else if (c == '.') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String readResource(String name) {
        try (InputStream stream = AdminSettingsCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name + " 를 찾을 수 없어요(src/main/resources)");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(name + " 를 읽지 못했어요", e);
        }
    }
}
