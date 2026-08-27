package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;

import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Harmless bootstrap hook used only to prove that MiniWx was loaded in the
 * WeChat main process and to capture the host Context for later local/UI hooks.
 */
public final class HostLifecycleHook implements HookItem {
    private static volatile Context hostContext;

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
                    }
                }
        );
    }
}
