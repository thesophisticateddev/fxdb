package org.fxsql.ui;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared background executor for UI-triggered work.
 *
 * Keeps heavy processing (database calls, file I/O, network) off the JavaFX
 * Application Thread without every controller owning its own thread pool.
 * All threads are daemons, so they never prevent application exit.
 */
public final class UiExecutors {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "fxdb-background-" + THREAD_COUNTER.getAndIncrement());
        t.setDaemon(true);
        return t;
    };

    private static final ExecutorService BACKGROUND = Executors.newCachedThreadPool(FACTORY);

    private UiExecutors() {
    }

    /** Shared daemon pool for short-to-medium background work. */
    public static ExecutorService background() {
        return BACKGROUND;
    }

    /**
     * Runs a {@link Task} on the shared pool. Use the task's
     * setOnSucceeded/setOnFailed handlers for UI updates — they already run
     * on the FX thread.
     */
    public static <T> Future<?> submit(Task<T> task) {
        return BACKGROUND.submit(task);
    }

    /** Runs a plain runnable on the shared pool. */
    public static Future<?> run(Runnable runnable) {
        return BACKGROUND.submit(runnable);
    }

    /** Called once on application shutdown. */
    public static void shutdown() {
        BACKGROUND.shutdownNow();
    }
}
