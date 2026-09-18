package com.tntbbp.myminecraft.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹 관리자(gn-admin)가 수집하는 활동/거래/관리 기록을 {@code plugins/MyMinecraft/logs/<채널>-YYYY-MM-DD.jsonl}
 * (UTC 날짜)에 한 줄에 한 이벤트씩 남긴다. 형식은 gn-admin {@code docs/api-bridge.md} §5를 따른다.
 *
 * <p>{@link #write}는 어느 스레드에서 불러도 되고 <b>절대 기다리지 않는다</b>. 이벤트는 큐에 넣기만 하고 실제
 * 파일 쓰기는 전용 스레드 1개가 한다. 큐가 가득 차면 그 이벤트는 버리고 개수만 세었다가, admin 채널에
 * {@code log_dropped} 한 줄로 남긴다 (메인 스레드·비동기 채팅 스레드를 막지 않기 위함).
 *
 * <p>{@link #write}에 넘긴 {@code data}는 쓰기 스레드가 나중에 읽으므로, 넘긴 뒤에는 수정하지 않는다.
 */
public class EventLog {

    /** 기록 채널. 파일 이름 앞부분이 된다. */
    public enum Channel {
        ACTIVITY("activity"),
        TRADE("trade"),
        ADMIN("admin");

        private final String fileKey;

        Channel(String fileKey) {
            this.fileKey = fileKey;
        }

        public String fileKey() {
            return fileKey;
        }
    }

    /** JSONL 스키마 버전 ({@code v}). */
    public static final int SCHEMA_VERSION = 1;
    public static final String SYSTEM_ACTOR = "system";

    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Pattern FILE_PATTERN =
            Pattern.compile("^(activity|trade|admin)-(\\d{4}-\\d{2}-\\d{2})\\.jsonl$");
    private static final int MIN_QUEUE_LIMIT = 100;
    private static final long POLL_MILLIS = 1000L;
    private static final long DROP_REPORT_INTERVAL_MILLIS = 10_000L;
    private static final long RETENTION_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000;
    private static final long IO_WARNING_INTERVAL_MILLIS = 60_000L;
    private static final long FLUSH_WAIT_MILLIS = 5_000L;

    private record Entry(Channel channel, long ts, String type, String actor, JsonObject data) {
    }

    /** 쓰기 스레드만 만지는 열린 파일. 날짜가 바뀌면 닫고 새로 연다. */
    private static final class OpenFile {
        private final LocalDate date;
        private final Writer writer;

        private OpenFile(LocalDate date, Writer writer) {
            this.date = date;
            this.writer = writer;
        }
    }

    private final File directory;
    private final int retentionDays;
    private final Logger logger;
    private final LongSupplier clock;
    private final BlockingQueue<Entry> queue;
    private final AtomicLong seq = new AtomicLong();
    private final Map<Channel, AtomicLong> dropped = new EnumMap<>(Channel.class);
    private final AtomicLong totalDropped = new AtomicLong();
    private final Map<Channel, OpenFile> openFiles = new EnumMap<>(Channel.class);

    private volatile boolean closed;
    private Thread writerThread;
    private long lastDropReportMillis;
    private long nextRetentionCheckMillis;
    private long lastIoWarningMillis;

    public EventLog(MyMinecraftPlugin plugin) {
        this(new File(plugin.getDataFolder(), "logs"),
                plugin.getConfig().getInt("activity-log.queue-limit", 10000),
                plugin.getConfig().getInt("activity-log.retention-days", 90),
                plugin.getLogger(),
                System::currentTimeMillis);
        start();
    }

    /** 테스트용: 쓰기 스레드는 {@link #start()}를 불러야 돈다. */
    EventLog(File directory, int queueLimit, int retentionDays, Logger logger, LongSupplier clock) {
        this.directory = directory;
        this.retentionDays = retentionDays;
        this.logger = logger;
        this.clock = clock;
        this.queue = new ArrayBlockingQueue<>(Math.max(MIN_QUEUE_LIMIT, queueLimit));
        for (Channel channel : Channel.values()) {
            dropped.put(channel, new AtomicLong());
        }
    }

    synchronized void start() {
        if (writerThread != null || closed) {
            return;
        }
        writerThread = new Thread(this::runWriter, "MyMinecraft-EventLog");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    /**
     * 이벤트 하나를 기록 큐에 넣는다. 어느 스레드에서나 호출 가능하며 기다리지 않는다.
     *
     * @param actor 원인 주체(플레이어 이름 / 웹 사용자 / {@code "system"} / {@code "console"}). null이면 {@code "system"}.
     */
    public void write(Channel channel, String type, String actor, JsonObject data) {
        if (closed || channel == null || type == null) {
            return;
        }
        Entry entry = new Entry(channel, clock.getAsLong(), type, actor == null ? SYSTEM_ACTOR : actor,
                data == null ? new JsonObject() : data);
        if (!queue.offer(entry)) {
            dropped.get(channel).incrementAndGet();
            totalDropped.incrementAndGet();
        }
    }

    /** 이번 실행 중 큐가 가득 차 버린 이벤트 수(누적, 진단용). */
    public long droppedCount() {
        return totalDropped.get();
    }

    /** 아직 파일에 쓰지 못하고 큐에 남은 이벤트 수(진단용). */
    public int pendingCount() {
        return queue.size();
    }

    /**
     * 더 이상 받지 않고, 큐에 남은 이벤트를 모두 파일에 쓴 뒤 닫는다. onDisable 맨 마지막에 부른다.
     * 쓰기 스레드가 {@value #FLUSH_WAIT_MILLIS}ms 안에 끝나지 않으면 기다리지 않고 돌아온다.
     */
    public void flush() {
        Thread thread;
        synchronized (this) {
            closed = true;
            thread = writerThread;
        }
        if (thread == null) {
            // 쓰기 스레드가 없었으면(테스트 등) 여기서 직접 비운다.
            drainAndClose();
            return;
        }
        thread.interrupt();
        try {
            thread.join(FLUSH_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            logger.warning("활동 기록을 제한 시간 안에 모두 쓰지 못했습니다. 남은 기록: " + queue.size() + "건");
        }
    }

    /** JSONL 한 줄(줄바꿈 제외)을 만든다. 순수 함수. */
    public static String serialize(long seq, long ts, String type, String actor, JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("ts", ts);
        root.addProperty("seq", seq);
        root.addProperty("type", type);
        root.addProperty("actor", actor == null ? SYSTEM_ACTOR : actor);
        root.add("data", data == null ? new JsonObject() : data);
        return GSON.toJson(root);
    }

    /** 기록 파일 이름이 보관 기간({@code retentionDays}일)을 넘겼는지. 기록 파일 형식이 아니면 false. */
    public static boolean isExpired(String fileName, LocalDate today, int retentionDays) {
        if (retentionDays <= 0 || fileName == null) {
            return false;
        }
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(2));
            return date.isBefore(today.minusDays(retentionDays));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** 이 채널·날짜의 파일 이름. */
    public static String fileName(Channel channel, LocalDate date) {
        return channel.fileKey() + "-" + date + ".jsonl";
    }

    private void runWriter() {
        long now = clock.getAsLong();
        lastDropReportMillis = now;
        deleteExpiredFiles(now);
        while (true) {
            Entry entry = null;
            if (!closed) {
                try {
                    entry = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // flush()가 깨웠다. 아래에서 closed를 보고 남은 것을 비운다.
                }
            }
            if (entry != null) {
                writeEntry(entry);
            }
            Entry next;
            while ((next = queue.poll()) != null) {
                writeEntry(next);
            }
            now = clock.getAsLong();
            reportDrops(now, false);
            flushWriters();
            if (now >= nextRetentionCheckMillis) {
                deleteExpiredFiles(now);
            }
            if (closed) {
                break;
            }
        }
        drainAndClose();
    }

    private synchronized void drainAndClose() {
        Entry next;
        while ((next = queue.poll()) != null) {
            writeEntry(next);
        }
        reportDrops(clock.getAsLong(), true);
        for (OpenFile open : openFiles.values()) {
            try {
                open.writer.close();
            } catch (IOException e) {
                warnIo("활동 기록 파일을 닫지 못했습니다: " + e.getMessage());
            }
        }
        openFiles.clear();
    }

    private void writeEntry(Entry entry) {
        String line = serialize(seq.incrementAndGet(), entry.ts(), entry.type(), entry.actor(), entry.data());
        writeLine(entry.channel(), line);
    }

    private void writeLine(Channel channel, String line) {
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), ZoneOffset.UTC);
        try {
            Writer writer = writerFor(channel, today);
            writer.write(line);
            writer.write('\n');
        } catch (IOException e) {
            closeQuietly(channel);
            warnIo("활동 기록을 쓰지 못했습니다 (" + channel.fileKey() + "): " + e.getMessage());
        }
    }

    private Writer writerFor(Channel channel, LocalDate today) throws IOException {
        OpenFile open = openFiles.get(channel);
        if (open != null && open.date.equals(today)) {
            return open.writer;
        }
        if (open != null) {
            closeQuietly(channel);
        }
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("기록 폴더를 만들 수 없습니다: " + directory);
        }
        File file = new File(directory, fileName(channel, today));
        // 서버가 비정상 종료되면 이 파일의 마지막 줄이 개행 없이 끊겨 있을 수 있다. 이어 쓰기 전에
        // 확인해서, 그렇다면 개행을 하나 먼저 써 이전 조각과 새 이벤트가 한 줄로 붙지 않게 한다.
        boolean needsLeadingNewline = fileEndsWithoutNewline(file);
        Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8));
        if (needsLeadingNewline) {
            writer.write('\n');
        }
        openFiles.put(channel, new OpenFile(today, writer));
        return writer;
    }

    /** {@code file}이 존재하고 크기가 0보다 크면서 마지막 바이트가 개행({@code \n})이 아닌지. 순수 함수. */
    static boolean fileEndsWithoutNewline(File file) {
        if (!file.isFile()) {
            return false;
        }
        long length = file.length();
        if (length <= 0) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(length - 1);
            return raf.read() != '\n';
        } catch (IOException e) {
            return false;
        }
    }

    private void flushWriters() {
        for (Map.Entry<Channel, OpenFile> open : Map.copyOf(openFiles).entrySet()) {
            try {
                open.getValue().writer.flush();
            } catch (IOException e) {
                closeQuietly(open.getKey());
                warnIo("활동 기록을 파일에 내보내지 못했습니다: " + e.getMessage());
            }
        }
    }

    private void closeQuietly(Channel channel) {
        OpenFile open = openFiles.remove(channel);
        if (open == null) {
            return;
        }
        try {
            open.writer.close();
        } catch (IOException ignored) {
            // 이미 문제가 난 파일이므로 무시
        }
    }

    /** 버린 개수를 admin 채널에 log_dropped로 남기고 0으로 되돌린다. */
    private void reportDrops(long now, boolean force) {
        boolean due = force || queue.isEmpty() || now - lastDropReportMillis >= DROP_REPORT_INTERVAL_MILLIS;
        if (!due) {
            return;
        }
        for (Channel channel : Channel.values()) {
            long count = dropped.get(channel).getAndSet(0);
            if (count <= 0) {
                continue;
            }
            JsonObject data = new JsonObject();
            data.addProperty("channel", channel.fileKey());
            data.addProperty("count", count);
            writeLine(Channel.ADMIN, serialize(seq.incrementAndGet(), now, "log_dropped", SYSTEM_ACTOR, data));
        }
        lastDropReportMillis = now;
    }

    private void deleteExpiredFiles(long now) {
        nextRetentionCheckMillis = now + RETENTION_CHECK_INTERVAL_MILLIS;
        if (retentionDays <= 0) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        int deleted = 0;
        for (File file : files) {
            if (file.isFile() && isExpired(file.getName(), today, retentionDays) && file.delete()) {
                deleted++;
            }
        }
        if (deleted > 0) {
            logger.info("보관 기간(" + retentionDays + "일)이 지난 활동 기록 파일 " + deleted + "개를 삭제했습니다.");
        }
    }

    private void warnIo(String message) {
        long now = clock.getAsLong();
        if (now - lastIoWarningMillis >= IO_WARNING_INTERVAL_MILLIS) {
            lastIoWarningMillis = now;
            logger.warning(message);
        }
    }
}
