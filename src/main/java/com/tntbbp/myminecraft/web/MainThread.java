package com.tntbbp.myminecraft.web;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 통로 HTTP 스레드에서 Bukkit 상태를 만지는 작업을 메인 스레드로 넘겨 실행하고 결과를 기다린다.
 *
 * <p>타임아웃 규칙: 제한 시간 안에 메인 스레드가 작업을 <b>시작하지 못하면</b> 작업을 취소하고(나중에도 실행되지
 * 않음) {@link MainThreadTimeoutException}을 던진다 → 같은 X-Request-Id로 재시도해도 중복 실행이 없다.
 * 이미 시작한 작업은 끝날 때까지 조금 더 기다린다(최대 {@value #RUNNING_GRACE_MILLIS}ms).
 *
 * <p>서버 종료 중이면 즉시 {@link ServerStoppingException}. 1초 heartbeat로 메인 스레드 지연을 잰다.
 */
public final class MainThread {

    /** 메인 스레드가 제한 시간 안에 작업을 처리하지 못함 → 504 main_thread_timeout. */
    public static final class MainThreadTimeoutException extends Exception {
        private final boolean started;

        public MainThreadTimeoutException(boolean started) {
            super(started ? "메인 스레드 작업이 제한 시간 안에 끝나지 않았습니다."
                    : "메인 스레드가 제한 시간 안에 작업을 시작하지 못했습니다.");
            this.started = started;
        }

        /** true면 작업이 이미 시작돼서 나중에 끝났을 수도 있다(드묾). */
        public boolean started() {
            return started;
        }
    }

    /** 서버 종료 중 → 503 server_stopping. */
    public static final class ServerStoppingException extends Exception {
        public ServerStoppingException() {
            super("서버가 종료 중입니다.");
        }
    }

    private static final int PENDING = 0;
    private static final int STARTED = 1;
    private static final int CANCELLED = 2;
    private static final long RUNNING_GRACE_MILLIS = 60_000L;
    private static final long HEARTBEAT_TICKS = 20L;
    private static final long HEARTBEAT_EXPECTED_MILLIS = 1000L;

    private record Pending(AtomicInteger state, CompletableFuture<?> future) {
    }

    private final Plugin plugin;
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean stopping;
    private volatile long lastBeatNanos;
    private BukkitTask heartbeat;

    public MainThread(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 메인 스레드에서 호출. 1초마다 heartbeat를 찍는다. */
    public void startHeartbeat() {
        lastBeatNanos = System.nanoTime();
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, () -> lastBeatNanos = System.nanoTime(),
                HEARTBEAT_TICKS, HEARTBEAT_TICKS);
    }

    /** 통로 정지 시(onDisable, 메인 스레드) 호출. 대기 중인 작업을 모두 취소하고 이후 요청은 즉시 거부한다. */
    public void markStopping() {
        stopping = true;
        if (heartbeat != null) {
            heartbeat.cancel();
            heartbeat = null;
        }
        for (Pending entry : pending) {
            if (entry.state().compareAndSet(PENDING, CANCELLED)) {
                entry.future().completeExceptionally(new ServerStoppingException());
            }
        }
    }

    public boolean isStopping() {
        return stopping || Bukkit.isStopping();
    }

    /** 마지막 heartbeat 이후 기대 간격(1초)을 넘긴 시간(ms). 진단값이며 요청을 거부하는 데 쓰지 않는다. */
    public long mainThreadLagMs() {
        long beat = lastBeatNanos;
        if (beat == 0L) {
            return 0L;
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beat);
        return Math.max(0L, elapsedMillis - HEARTBEAT_EXPECTED_MILLIS);
    }

    /**
     * {@code callable}을 메인 스레드에서 실행하고 결과를 돌려준다. 이미 메인 스레드면 바로 실행한다.
     * {@code callable}이 던진 예외(예: {@link BridgeException})는 그대로 다시 던진다.
     */
    public <T> T callSync(Callable<T> callable, long timeoutMillis) throws Exception {
        if (isStopping()) {
            throw new ServerStoppingException();
        }
        if (Bukkit.isPrimaryThread()) {
            return callable.call();
        }
        AtomicInteger state = new AtomicInteger(PENDING);
        CompletableFuture<T> future = new CompletableFuture<>();
        Pending entry = new Pending(state, future);
        pending.add(entry);
        try {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!state.compareAndSet(PENDING, STARTED)) {
                        return;
                    }
                    try {
                        future.complete(callable.call());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (IllegalPluginAccessException e) {
                throw new ServerStoppingException();
            }
            if (stopping) {
                // markStopping()과 엇갈려 등록된 경우
                if (state.compareAndSet(PENDING, CANCELLED)) {
                    throw new ServerStoppingException();
                }
            }
            try {
                return future.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (state.compareAndSet(PENDING, CANCELLED)) {
                    throw new MainThreadTimeoutException(false);
                }
                try {
                    return future.get(RUNNING_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e2) {
                    throw new MainThreadTimeoutException(true);
                }
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (state.compareAndSet(PENDING, CANCELLED)) {
                throw new ServerStoppingException();
            }
            throw new MainThreadTimeoutException(true);
        } finally {
            pending.remove(entry);
        }
    }
}
