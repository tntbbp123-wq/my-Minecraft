package com.tntbbp.myminecraft.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 서버에 이미 있는 {@code config.yml}을 새 버전에 맞춰 자동으로 채워준다.
 *
 * <p>버킷의 {@code saveDefaultConfig()}는 <b>파일이 없을 때만</b> 기본 설정을 복사한다. 그래서 플러그인을
 * 올려도 기존 서버의 {@code config.yml}에는 새로 생긴 항목이 영원히 생기지 않는다. 값을 읽는 쪽이
 * {@code getInt("키", 기본값)}처럼 기본값을 들고 있으면 문제가 없지만, <b>목록으로 정의되는 기능</b>
 * (재료 18종의 {@code materials.list} 등)은 설정에 없으면 그 기능 자체가 조용히 사라진다.
 *
 * <p>그래서 서버를 켤 때마다 두 가지를 한다.
 *
 * <ol>
 *   <li><b>빠진 항목 채우기</b> — jar 안의 기본 설정에는 있는데 서버 파일에 없는 항목만 넣는다.
 *       이미 있는 값은 손대지 않는다. 관리자가 고쳐 둔 값이 되돌아가면 안 되기 때문이다.</li>
 *   <li><b>기본값이 바뀐 항목 옮기기</b> — {@link #MIGRATIONS}에 적어둔 것만, 그리고 그 값이
 *       <b>아직 옛 기본값 그대로일 때만</b> 새 값으로 바꾼다. 관리자가 직접 고쳐 둔 값이면 건너뛴다.</li>
 * </ol>
 *
 * <p>바꿀 것이 하나도 없으면 <b>파일을 아예 쓰지 않는다.</b> 매번 서버를 켤 때마다 멀쩡한 파일을
 * 다시 쓰는 것이 가장 위험하기 때문이다. 바꿀 것이 있을 때는 먼저 {@code config.yml.bak-날짜시각}으로
 * 원본을 복사해 두고, 무엇을 채웠고 무엇을 옮겼는지 콘솔에 남긴다.
 */
public final class ConfigUpdater {

    /**
     * 설정 파일 구조 버전. 기본값이 바뀌는 변경을 넣을 때마다 1 올리고 {@link #MIGRATIONS}에 줄을 추가한다.
     * (항목을 <b>추가</b>만 할 때는 올릴 필요가 없다. 빠진 항목 채우기가 알아서 처리한다.)
     */
    public static final int CURRENT_VERSION = 2;

    /** 설정 파일에 저장되는 구조 버전 키. */
    public static final String VERSION_PATH = "config-version";

    /**
     * 기본값이 바뀐 항목.
     *
     * @param toVersion  이 이주를 적용하면 올라가는 버전
     * @param path       설정 경로
     * @param oldDefault 옛 기본값. 서버 파일의 값이 <b>이것과 같을 때만</b> 바꾼다
     * @param newValue   새로 넣을 값
     * @param reason     콘솔에 남길 이유
     */
    public record Migration(int toVersion, String path, Object oldDefault, Object newValue, String reason) {
    }

    /** 버전 순서대로 적어둔다. */
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(2, "malyongdo.combo-window-seconds", 10, 25,
                    "말룡도 1초식→2초식 연계 유지 시간 버프")
    );

    /**
     * 갱신 결과.
     *
     * @param addedBlocks   새로 채운 항목(가장 바깥 단위로 묶은 이름)
     * @param applied       실제로 적용한 이주
     * @param skipped       관리자가 고쳐 둔 값이라 건너뛴 이주
     * @param versionBumped 구조 버전만 올라갔는지. 이것만 바뀌어도 저장해야 건너뛴 이주를
     *                      켤 때마다 다시 알리지 않는다
     */
    public record Result(List<String> addedBlocks, List<Migration> applied, List<Migration> skipped,
                         boolean versionBumped) {

        public boolean changed() {
            return !addedBlocks.isEmpty() || !applied.isEmpty() || versionBumped;
        }
    }

    private ConfigUpdater() {
    }

    /**
     * 서버의 {@code config.yml}을 제자리에서 갱신한다. 바꿀 것이 없으면 파일을 쓰지 않는다.
     *
     * <p>실패해도 서버를 멈추지는 않는다. 설정 갱신에 실패했다고 플러그인이 안 켜지면 더 곤란하다.
     * 대신 무엇이 안 됐는지 콘솔에 크게 남긴다.
     */
    public static void run(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            // saveDefaultConfig()가 방금 만들었어야 한다. 없다면 채울 대상도 없다.
            return;
        }

        YamlConfiguration defaults = loadDefaults(plugin);
        if (defaults == null) {
            log.severe("jar 안의 기본 config.yml을 읽지 못했습니다. 설정 자동 갱신을 건너뜁니다.");
            return;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        Result result = merge(current, defaults);

        if (!result.changed()) {
            for (Migration migration : result.skipped()) {
                log.info("[설정] " + migration.path() + " 은(는) 직접 고치신 값이라 그대로 뒀습니다"
                        + " (새 기본값은 " + migration.newValue() + ", " + migration.reason() + ").");
            }
            return;
        }

        try {
            Path backup = backup(file.toPath());
            AtomicYaml.save(current, file);
            log.info("[설정] config.yml을 새 버전에 맞춰 갱신했습니다. 원본은 "
                    + backup.getFileName() + " 으로 남겨뒀습니다.");
        } catch (IOException e) {
            log.severe("[설정] config.yml 갱신에 실패했습니다: " + e.getMessage()
                    + " — 파일은 건드리지 않았습니다. 빠진 항목은 직접 넣어주세요.");
            return;
        }

        for (String block : result.addedBlocks()) {
            log.info("[설정] 새 항목을 채웠습니다: " + block);
        }
        for (Migration migration : result.applied()) {
            log.info("[설정] " + migration.path() + " 을(를) " + migration.oldDefault()
                    + " → " + migration.newValue() + " 로 옮겼습니다 (" + migration.reason() + ").");
        }
        for (Migration migration : result.skipped()) {
            log.info("[설정] " + migration.path() + " 은(는) 직접 고치신 값이라 그대로 뒀습니다"
                    + " (새 기본값은 " + migration.newValue() + ", " + migration.reason() + ").");
        }

        plugin.reloadConfig();
    }

    /** jar 안에 들어 있는 기본 {@code config.yml}. */
    private static YamlConfiguration loadDefaults(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** 덮어쓰기 전에 원본을 옆에 복사해 둔다. */
    private static Path backup(Path file) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = file.resolveSibling(file.getFileName() + ".bak-" + stamp);
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * {@code current}를 제자리에서 갱신한다. 파일 입출력이 없어 그대로 시험할 수 있다.
     *
     * <p><b>순서가 중요하다.</b> 저장된 버전을 <b>맨 먼저</b> 읽는다. 빠진 항목을 먼저 채우면
     * {@code config-version}까지 새 값으로 채워져서, 정작 옮겨야 할 값을 이미 최신이라고 착각한다.
     */
    public static Result merge(YamlConfiguration current, YamlConfiguration defaults) {
        int storedVersion = current.getInt(VERSION_PATH, 1);

        Set<String> before = Set.copyOf(current.getKeys(true));
        List<String> addedBlocks = new ArrayList<>(addMissing(current, defaults, before));
        // 구조 버전은 내부 살림이라 "새로 채운 항목"으로 알릴 필요가 없다.
        addedBlocks.remove(VERSION_PATH);

        List<Migration> applied = new ArrayList<>();
        List<Migration> skipped = new ArrayList<>();
        for (Migration migration : MIGRATIONS) {
            if (migration.toVersion() <= storedVersion) {
                continue;   // 이미 지나온 버전
            }
            Object value = current.get(migration.path());
            if (value == null) {
                // 애초에 없던 항목이면 위에서 새 기본값으로 채워졌다. 옮길 것이 없다.
                continue;
            }
            if (equalsLoosely(value, migration.oldDefault())) {
                current.set(migration.path(), migration.newValue());
                applied.add(migration);
            } else if (!equalsLoosely(value, migration.newValue())) {
                skipped.add(migration);
            }
        }

        boolean versionBumped = storedVersion < CURRENT_VERSION;
        if (versionBumped) {
            current.set(VERSION_PATH, CURRENT_VERSION);
        }
        return new Result(List.copyOf(addedBlocks), List.copyOf(applied), List.copyOf(skipped),
                versionBumped);
    }

    /**
     * 기본 설정에는 있는데 서버 파일에 없는 <b>잎 항목</b>만 채운다. 이미 있는 값은 건드리지 않는다.
     *
     * @param before 채우기 전의 경로 집합. 어디까지가 통째로 새로 생긴 덩어리인지 가려내는 데 쓴다
     * @return 새로 생긴 덩어리 이름 (예: {@code materials}, {@code titan.attack-damage})
     */
    private static List<String> addMissing(YamlConfiguration current, YamlConfiguration defaults,
                                           Set<String> before) {
        Set<String> blocks = new LinkedHashSet<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.get(path) instanceof ConfigurationSection) {
                continue;   // 중간 마디는 잎을 넣을 때 알아서 생긴다
            }
            if (current.contains(path)) {
                continue;
            }
            current.set(path, defaults.get(path));
            copyComments(current, defaults, path);
            blocks.add(shallowestMissing(path, before));
        }

        // 구역을 설명하는 주석(예: "# 전설 등급 세이버 '볼트 세이버' ...")은 구역 이름 쪽에 붙어 있다.
        // 잎을 다 넣어 구역이 생긴 뒤에야 옮길 수 있어서 따로 한 번 더 돈다.
        for (String path : defaults.getKeys(true)) {
            if (defaults.get(path) instanceof ConfigurationSection
                    && !before.contains(path) && current.contains(path)) {
                copyComments(current, defaults, path);
            }
        }
        return List.copyOf(blocks);
    }

    /**
     * 새로 채운 잎 경로가 속한 <b>가장 바깥쪽 새 덩어리</b>의 이름.
     *
     * <p>{@code materials.list.성스러운태양석.name}처럼 깊은 경로를 200줄 찍는 대신 {@code materials}
     * 한 줄로 알려주기 위한 것이다.
     */
    private static String shallowestMissing(String path, Set<String> before) {
        String[] parts = path.split("\\.");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            if (!prefix.isEmpty()) {
                prefix.append('.');
            }
            prefix.append(part);
            if (!before.contains(prefix.toString())) {
                return prefix.toString();
            }
        }
        return path;
    }

    /** 새로 넣은 항목에는 기본 설정의 설명(주석)도 같이 옮겨 준다. */
    private static void copyComments(YamlConfiguration current, YamlConfiguration defaults, String path) {
        List<String> comments = defaults.getComments(path);
        if (!comments.isEmpty()) {
            current.setComments(path, comments);
        }
        List<String> inline = defaults.getInlineComments(path);
        if (!inline.isEmpty()) {
            current.setInlineComments(path, inline);
        }
    }

    /**
     * 설정에서 읽은 값과 코드에 적어둔 값을 비교한다.
     *
     * <p>YAML에서 {@code 10}은 Integer로, {@code 10.0}은 Double로 올라온다. 숫자는 값이 같으면
     * 같은 것으로 본다. 그렇지 않으면 {@code 10.0}이라고 적어 둔 관리자의 파일에서 이주가
     * 엉뚱하게 건너뛰어진다.
     */
    private static boolean equalsLoosely(Object a, Object b) {
        if (a instanceof Number left && b instanceof Number right) {
            return Double.compare(left.doubleValue(), right.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }
}
