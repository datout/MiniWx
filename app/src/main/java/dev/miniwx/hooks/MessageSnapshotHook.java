package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookResolveExecutor;
import dev.miniwx.wechat.MessageInfo;
import dev.miniwx.wechat.MessageSnapshotCache;
import dev.miniwx.wechat.ReflectionUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Captures newly inserted message objects so anti-recall can identify the original sender. */
public final class MessageSnapshotHook implements HookItem {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);

    @Override public String name() { return "MessageSnapshot"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!STARTED.compareAndSet(false, true)) return;
                Context host = (Context) param.args[0];
                HookResolveExecutor.submit("MessageSnapshot", () -> resolve(host, context));
            }
        });
    }

    private static void resolve(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir : host.getApplicationInfo().sourceDir;
        MethodData data;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            data = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings(
                            "MicroMsg.MsgInfoStorage",
                            "MsgInfo processAddMsg insert db error"
                    )
            )).singleOrThrow(() -> new IllegalStateException("MsgInfoStorage insert match is not unique"));
        }
        Method method = data.getMethodInstance(host.getClassLoader());
        if (!HOOKED.compareAndSet(false, true)) return;
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) { capture(param.args); }
            @Override protected void afterHookedMethod(MethodHookParam param) { capture(param.args); }
        });
        HookLog.i("MessageSnapshot active: " + method);
    }

    private static void capture(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (!ReflectionUtils.looksLikeMessageObject(arg)) continue;
            try {
                MessageSnapshotCache.put(new MessageInfo(arg));
            } catch (Throwable ignored) {
            }
            return;
        }
    }
}
