package dev.miniwx.wechat;

import android.app.Application;
import android.content.Context;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/**
 * Shared message-row binding service. The entry method is dynamically located using
 * WeChat's MvvmChattingItem log strings, then a small reflection layer resolves the
 * current row's root View and message object.
 */
public final class MessageViewApi implements HookItem {
    public interface Listener {
        void onBind(View view, MessageInfo message) throws Throwable;
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean METHOD_HOOKED = new AtomicBoolean(false);

    private static volatile Field adapterField;
    private static volatile Method adapterGetItem;

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    @Override public String name() { return "MessageViewApi"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context host = (Context) param.args[0];
                        if (!RESOLVE_STARTED.compareAndSet(false, true)) return;
                        try {
                            resolveAndHook(host, context);
                        } catch (Throwable t) {
                            HookLog.e("MessageViewApi resolver failed", t);
                        }
                    }
                }
        );
    }

    private static void resolveAndHook(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir
                : host.getApplicationInfo().sourceDir;

        MethodData data;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            data = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingEqStrings(
                                    "MicroMsg.MvvmChattingItem",
                                    "[onBindView]"
                            )
                    )
            ).singleOrThrow(() -> new IllegalStateException("chat onBindView match is not unique"));
        }

        Method bindMethod = data.getMethodInstance(host.getClassLoader());
        HookLog.i("MessageViewApi resolved: " + bindMethod);
        if (!METHOD_HOOKED.compareAndSet(false, true)) return;

        XposedBridge.hookMethod(bindMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.args == null || param.args.length < 3) return;
                    if (!(param.args[2] instanceof Integer index)) return;
                    View root = ReflectionUtils.firstViewField(param.args[0]);
                    if (root == null) return;
                    Object messageObject = resolveMessageObject(param.thisObject, index);
                    if (!ReflectionUtils.looksLikeMessageObject(messageObject)) return;
                    MessageInfo message = new MessageInfo(messageObject);
                    for (Listener listener : LISTENERS) {
                        try {
                            listener.onBind(root, message);
                        } catch (Throwable t) {
                            HookLog.e("message listener failed: " + listener.getClass().getName(), t);
                        }
                    }
                } catch (Throwable t) {
                    HookLog.e("MessageViewApi bind handler failed", t);
                }
            }
        });
        HookLog.i("MessageViewApi active, listeners=" + LISTENERS.size());
    }

    private static Object resolveMessageObject(Object chattingItem, int index) {
        if (chattingItem == null) return null;

        Field cachedField = adapterField;
        Method cachedMethod = adapterGetItem;
        if (cachedField != null && cachedMethod != null) {
            try {
                Object adapter = cachedField.get(chattingItem);
                if (adapter != null) {
                    Object candidate = cachedMethod.invoke(adapter, index);
                    if (ReflectionUtils.looksLikeMessageObject(candidate)) return candidate;
                }
            } catch (Throwable ignored) {
                adapterField = null;
                adapterGetItem = null;
            }
        }

        for (Class<?> c = chattingItem.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object adapter = field.get(chattingItem);
                    if (adapter == null) continue;
                    Method getItem = ReflectionUtils.findMethod(adapter.getClass(), "getItem", int.class);
                    if (getItem == null) continue;
                    Object candidate = getItem.invoke(adapter, index);
                    if (!ReflectionUtils.looksLikeMessageObject(candidate)) continue;
                    adapterField = field;
                    adapterGetItem = getItem;
                    return candidate;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
