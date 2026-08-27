package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookResolveExecutor;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Best-effort removal of the classic 100-message multi-select limit. */
public final class MessageSelectionLimitHook implements HookItem {
    private static final int LIMIT = 100;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();
    private static volatile Context hostContext;

    @Override public String name() { return "MessageSelectionLimit"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                hostContext = (Context) param.args[0];
                if (!STARTED.compareAndSet(false, true)) return;
                HookResolveExecutor.submit("MessageSelectionLimit", () -> resolve(hostContext, context));
            }
        });
    }

    private static void resolve(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir : host.getApplicationInfo().sourceDir;
        MethodData probe;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            probe = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings(
                            "MicroMsg.ChattingDataAdapterV3",
                            "[handleMsgChange] isLockNotify:"
                    )
            )).singleOrThrow(() -> new IllegalStateException(
                    "当前微信未找到 ChattingDataAdapterV3；8.0.77 等版本可能已更换多选实现"));
        }

        Class<?> adapterClass = probe.getMethodInstance(host.getClassLoader()).getDeclaringClass();
        Field selectedField = null;
        for (Field field : adapterClass.getDeclaredFields()) {
            if (CopyOnWriteArraySet.class.isAssignableFrom(field.getType())) {
                if (selectedField != null) throw new IllegalStateException("selected message set field is not unique");
                field.setAccessible(true);
                selectedField = field;
            }
        }
        if (selectedField == null) throw new IllegalStateException("selected message set field not found");

        Method toggle = null;
        for (Method method : adapterClass.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (method.getReturnType() == boolean.class && p.length == 1
                    && "com.tencent.mm.plugin.msg.MsgIdTalker".equals(p[0].getName())) {
                if (toggle != null) throw new IllegalStateException("toggle selection method is not unique");
                method.setAccessible(true);
                toggle = method;
            }
        }
        if (toggle == null) throw new IllegalStateException("toggle selection method not found");

        Field finalSelectedField = selectedField;
        XposedBridge.hookMethod(toggle, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Context context = hostContext;
                if (context == null || !ModuleConfigClient.getBoolean(context, FeatureFlags.MESSAGE_SELECTION_UNLIMITED)) return;
                @SuppressWarnings("unchecked")
                CopyOnWriteArraySet<Object> selected = (CopyOnWriteArraySet<Object>) finalSelectedField.get(param.thisObject);
                Object message = param.args[0];
                if (selected == null || message == null || selected.contains(message) || selected.size() < LIMIT) return;
                int removeCount = selected.size() - LIMIT + 1;
                List<Object> removed = new ArrayList<>(removeCount);
                for (Object item : selected) {
                    if (removed.size() >= removeCount) break;
                    removed.add(item);
                }
                selected.removeAll(removed);
                STATE.set(new State(selected, removed));
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                State state = STATE.get();
                if (state == null) return;
                try {
                    List<Object> remainingAndNew = new ArrayList<>(state.selected);
                    state.selected.clear();
                    state.selected.addAll(state.removed);
                    state.selected.addAll(remainingAndNew);
                } finally {
                    STATE.remove();
                }
            }
        });
        HookLog.i("MessageSelectionLimit active: " + toggle);
    }

    private record State(CopyOnWriteArraySet<Object> selected, List<Object> removed) {}
}
