package dev.miniwx.core;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.hooks.HostLifecycleHook;

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
            Context host = HostLifecycleHook.getHostContext();
            if (host != null) ModuleConfigClient.reportHookStatus(host, name, "解析中", "正在定位微信方法");
            try {
                task.run();
                long elapsed = System.currentTimeMillis() - start;
                HookLog.i(name + " resolver finished in " + elapsed + " ms");
                host = HostLifecycleHook.getHostContext();
                if (host != null) ModuleConfigClient.reportHookStatus(host, name, "正常", "解析完成 · " + elapsed + " ms");
            } catch (Throwable t) {
                HookLog.e(name + " resolver failed", t);
                host = HostLifecycleHook.getHostContext();
                if (host != null) ModuleConfigClient.reportHookStatus(host, name, "失败", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            }
        });
    }
}
