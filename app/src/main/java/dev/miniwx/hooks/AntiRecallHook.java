package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
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
 * Anti-recall implementation based on the parsed sysmsg/revokemsg map.
 * It does not modify network traffic. 0.4 adds user settings and an optional
 * local notice. Self-recall detection is intentionally conservative and uses
 * WeChat's replacement text until a database-backed sender check is added.
 */
public final class AntiRecallHook implements HookItem {
    private static final String TYPE_KEY = ".sysmsg.$type";
    private static final String REPLACE_KEY = ".sysmsg.revokemsg.replacemsg";
    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean PARSER_HOOKED = new AtomicBoolean(false);
    private static volatile Context hostContext;

    @Override public String name() { return "AntiRecall"; }
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
                        hostContext = (Context) param.args[0];
                        if (!RESOLVE_STARTED.compareAndSet(false, true)) return;
                        try {
                            installXmlParserHook(hostContext, context);
                        } catch (Throwable t) {
                            HookLog.e("AntiRecall resolver failed", t);
                        }
                    }
                }
        );
    }

    private static void installXmlParserHook(Context context, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");

        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir
                : context.getApplicationInfo().sourceDir;
        ClassLoader hostClassLoader = context.getClassLoader();

        HookLog.i("AntiRecall resolving XML parser with DexKit");
        MethodData methodData;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            methodData = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mm.sdk.platformtools")
                            .matcher(MethodMatcher.create()
                                    .usingEqStrings("MicroMsg.SDK.XmlParser", "[ %s ]"))
            ).singleOrThrow(() -> new IllegalStateException("WeChat XML parser match is not unique"));
        }

        Method parserMethod = methodData.getMethodInstance(hostClassLoader);
        HookLog.i("AntiRecall XML parser resolved: " + parserMethod);
        if (!PARSER_HOOKED.compareAndSet(false, true)) return;

        XposedBridge.hookMethod(parserMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    blockRecallIfNeeded(param);
                } catch (Throwable t) {
                    HookLog.e("AntiRecall parse handler failed", t);
                }
            }
        });
        HookLog.i("AntiRecall active");
    }

    @SuppressWarnings("unchecked")
    private static void blockRecallIfNeeded(XC_MethodHook.MethodHookParam param) {
        Context context = hostContext;
        if (context == null || !ModuleConfigClient.getBoolean(context, FeatureFlags.ANTI_RECALL)) return;
        if (param.args == null || param.args.length < 2) return;
        if (!(param.args[0] instanceof String xmlContent) || !(param.args[1] instanceof String rootTag)) return;
        if (!"sysmsg".equals(rootTag) || !xmlContent.contains("revokemsg")) return;
        if (!(param.getResult() instanceof Map<?, ?> rawMap)) return;

        Map<Object, Object> result = (Map<Object, Object>) rawMap;
        if (!"revokemsg".equals(result.get(TYPE_KEY))) return;

        String replaceMessage = String.valueOf(result.get(REPLACE_KEY));
        boolean looksLikeOwnRecall = replaceMessage.startsWith("你撤回")
                || replaceMessage.startsWith("You recalled")
                || replaceMessage.contains("You recalled a message");

        if (looksLikeOwnRecall
                && ModuleConfigClient.getBoolean(context, FeatureFlags.OWN_RECALL_NORMAL)) {
            HookLog.i("AntiRecall allowed probable self revoke");
            return;
        }

        result.put(TYPE_KEY, null);
        HookLog.i("AntiRecall blocked one local revoke event");

        if (ModuleConfigClient.getBoolean(context, FeatureFlags.RECALL_NOTICE)) {
            String notice = (replaceMessage == null || "null".equals(replaceMessage))
                    ? "检测到一条消息被撤回，已保留原消息"
                    : replaceMessage + "（MiniWx 已阻止）";
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show();
        }
    }
}
