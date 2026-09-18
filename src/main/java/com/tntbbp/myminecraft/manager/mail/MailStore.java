package com.tntbbp.myminecraft.manager.mail;

import com.tntbbp.myminecraft.model.Mailbox;
import com.tntbbp.myminecraft.util.AtomicYaml;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 우편 파일 입출력 ({@code plugins/MyMinecraft/mail/<uuid>.yml}, 전역 id 카운터 {@code mail/counter.yml}).
 *
 * <p>저장은 <b>내용(YAML 문자열)을 부른 스레드(메인)에서 만들고</b>, 디스크 쓰기는 전용 쓰기 스레드 1개가
 * {@link AtomicYaml#write}로 순서대로 한다 — 전체 발송처럼 파일을 많이 쓸 때 메인 스레드가 멈추지 않게 하기 위함.
 *
 * <ul>
 *   <li>아직 디스크에 쓰지 못한 최신 내용은 {@code pending}에 남아 있고, 읽기는 {@code pending}을 먼저 본다.
 *       쓰기가 <b>끝난 뒤에만</b> {@code pending}에서 지우므로 "캐시에서 빠졌는데 디스크는 옛 내용"인 틈이 없다
 *       (수령한 우편이 되살아나는 복제 방지). 쓰기가 실패하면 {@code pending}에 남겨 두고 다음 저장·종료 때 다시 쓴다.</li>
 *   <li>쓰기 스레드는 먼저 들어온 순서대로 처리한다. 카운터는 우편보다 먼저 저장 요청되므로, 디스크의 우편 id는
 *       항상 디스크 카운터보다 작다(서버가 중간에 죽어도 id 재사용 없음).</li>
 * </ul>
 */
public final class MailStore {

    private static final String COUNTER_FILE = "counter.yml";
    private static final String YML = ".yml";

    private final Path directory;
    private final Logger logger;
    private final Map<Path, String> pending = new ConcurrentHashMap<>();
    private final ExecutorService writer;

    public MailStore(Path directory, Logger logger) {
        this.directory = directory.toAbsolutePath();
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MyMinecraft-MailWriter");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Path directory() {
        return directory;
    }

    Path mailboxPath(UUID owner) {
        return directory.resolve(owner + YML);
    }

    // ---------------------------------------------------------------- 우편함

    /**
     * 우편함을 읽는다(쓰기 대기 중인 최신 내용 우선). 파일이 없으면 빈 우편함.
     * 파일이 깨져 있으면 {@code .broken-<시각>} 복사본을 남기고 빈 우편함으로 시작한다(원본은 다음 저장 때 덮어씀).
     * 아무 스레드에서나 호출할 수 있다.
     */
    public Mailbox loadMailbox(UUID owner) {
        return loadMailbox(owner, true);
    }

    /**
     * @param backupBroken 손상된 파일이면 복사본을 남길지(주기 점검처럼 읽기만 할 때는 false — 복사본이 쌓이지 않게)
     */
    public Mailbox loadMailbox(UUID owner, boolean backupBroken) {
        Path path = mailboxPath(owner);
        String text;
        try {
            text = readText(path);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "우편함 파일을 읽지 못했습니다: " + path.getFileName(), e);
            return new Mailbox(owner);
        }
        if (text == null || text.isBlank()) {
            return new Mailbox(owner);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            if (backupBroken) {
                backupBroken(path);
                logger.severe("우편함 파일이 손상되어 빈 우편함으로 시작합니다(원본은 .broken 복사본으로 보관): "
                        + path.getFileName() + " — " + e.getMessage());
            }
            return new Mailbox(owner);
        }
        return MailCodec.read(owner, yaml);
    }

    /** 우편함 내용을 지금(부른 스레드에서) YAML로 만들고, 디스크 쓰기는 쓰기 스레드에 맡긴다. */
    public void saveMailbox(Mailbox mailbox) {
        submit(mailboxPath(mailbox.owner()), MailCodec.write(mailbox).saveToString());
    }

    /** 우편함 파일이 있는 플레이어 목록(파일 이름이 UUID인 것만). */
    public List<UUID> owners() {
        List<UUID> owners = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + YML)) {
                for (Path path : stream) {
                    UUID owner = ownerOf(path);
                    if (owner != null) {
                        owners.add(owner);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "우편 폴더 목록을 읽지 못했습니다", e);
            }
        }
        for (Path path : pending.keySet()) {
            UUID owner = ownerOf(path);
            if (owner != null && !owners.contains(owner)) {
                owners.add(owner);
            }
        }
        return owners;
    }

    // ---------------------------------------------------------------- 전역 id 카운터

    /**
     * 다음에 쓸 우편 id. 카운터 파일이 없으면(첫 실행·삭제됨) 모든 우편함을 훑어 가장 큰 id + 1.
     */
    public long loadNextId() {
        Path path = directory.resolve(COUNTER_FILE);
        try {
            String text = readText(path);
            if (text != null && !text.isBlank()) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(text);
                long next = yaml.getLong("next-id", 0L);
                if (next > 0) {
                    return next;
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            logger.warning("우편 id 카운터(mail/counter.yml)를 읽지 못해 우편함을 훑어 다시 계산합니다: " + e.getMessage());
        }
        long max = 0L;
        for (UUID owner : owners()) {
            max = Math.max(max, loadMailbox(owner).maxId());
        }
        return max + 1;
    }

    /** 다음 우편 id를 저장한다(우편함 저장보다 먼저 불러야 함). */
    public void saveNextId(long nextId) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        submit(directory.resolve(COUNTER_FILE), yaml.saveToString());
    }

    // ---------------------------------------------------------------- 쓰기

    /** 최신 내용: 쓰기 대기 중인 것 우선, 없으면 디스크. 파일도 없으면 null. */
    String readText(Path path) throws IOException {
        String queued = pending.get(path);
        if (queued != null) {
            return queued;
        }
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void submit(Path path, String content) {
        pending.put(path, content);
        try {
            writer.execute(() -> writePending(path));
        } catch (RejectedExecutionException e) {
            // 종료 뒤 저장: 그 자리에서 쓴다
            writePending(path);
        }
    }

    private void writePending(Path path) {
        String content = pending.get(path);
        if (content == null) {
            return;
        }
        try {
            AtomicYaml.write(content, path);
            pending.remove(path, content);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE, "우편 파일 저장 실패(다음 저장·종료 때 다시 시도): " + path.getFileName(), e);
        }
    }

    /** 쓰기 스레드를 멈추고 남은 내용을 모두 디스크에 쓴다. onDisable에서 호출. */
    public void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("우편 파일 쓰기가 늦어져 남은 내용을 직접 저장합니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Path path : new ArrayList<>(pending.keySet())) {
            writePending(path);
        }
    }

    /** 아직 디스크에 쓰지 못한 파일 수(진단·테스트용). */
    public int pendingCount() {
        return pending.size();
    }

    private void backupBroken(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.log(Level.WARNING, "손상된 우편함 파일을 복사하지 못했습니다: " + path.getFileName(), e);
        }
    }

    private static UUID ownerOf(Path path) {
        String name = path.getFileName().toString();
        if (!name.endsWith(YML)) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(0, name.length() - YML.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
