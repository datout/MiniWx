package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Captures the WeChat context and reports a small runtime heartbeat to MiniWx. */
public final class HostLifecycleHook implements HookItem {
    private static volatile Context hostContext;
    private static final AtomicBoolean HEARTBEAT_STARTED = new AtomicBoolean(false);
    private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MiniWx-Heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public static Context getHostContext() {
        return hostContext;
    }

    @Override
    public String name() {
        return "HostLifecycle";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        hostContext = (Context) param.args[0];
                        String version = "unknown";
                        try {
                            version = hostContext.getPackageManager()
                                    .getPackageInfo(hostContext.getPackageName(), 0)
                                    .versionName;
                        } catch (Throwable ignored) {
                        }
                        HookLog.i("WeChat attached, version=" + version
                                + ", process=" + context.loadPackageParam.processName);
                        String processName = context.loadPackageParam.processName;
                        ModuleConfigClient.reportRuntime(hostContext, version, processName);
                        startHeartbeat(hostContext, version, processName);
                    }
                }
        );
    }
    private static void startHeartbeat(Context context, String version, String processName) {
        if (!HEARTBEAT_STARTED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        HEARTBEAT.scheduleAtFixedRate(
                () -> ModuleConfigClient.reportRuntime(appContext, version, processName),
                4,
                4,
                TimeUnit.MINUTES
        );
    }

}
