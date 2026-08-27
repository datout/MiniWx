package dev.miniwx.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial background queue for expensive DexKit resolution so Application.attach is not blocked. */
public final class HookResolveExecutor {
    @FunctionalInterface
    public interface Task {
        void run() throws Throwable;
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MiniWx-DexResolver");
        thread.setDaemon(true);
        return thread;
    });

    private HookResolveExecutor() {}

    public static void submit(String name, Task task) {
        EXECUTOR.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                task.run();
                HookLog.i(name + " resolver finished in " + (System.currentTimeMillis() - start) + " ms");
            } catch (Throwable t) {
                HookLog.e(name + " resolver failed", t);
            }
        });
    }
}
