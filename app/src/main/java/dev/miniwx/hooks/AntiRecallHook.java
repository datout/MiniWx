package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.Map;
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
 * Minimal anti-recall implementation.
 *
 * <p>The hook locates WeChat's XML parser by stable string features instead of
 * hard-coding an obfuscated class/method name. After a sysmsg/revokemsg is
 * parsed, the local result map has its type cleared so normal downstream
 * recall handling does not consume/delete the original local message.</p>
 *
 * <p>This version intentionally does not forge network traffic and does not
 * implement environment/detection concealment.</p>
 */
public final class AntiRecallHook implements HookItem {
    private static final String TYPE_KEY = ".sysmsg.$type";
    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean PARSER_HOOKED = new AtomicBoolean(false);

    @Override
    public String name() {
        return "AntiRecall";
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
                        if (!RESOLVE_STARTED.compareAndSet(false, true)) {
                            return;
                        }

                        Context hostContext = (Context) param.args[0];
                        try {
                            installXmlParserHook(hostContext, context);
                        } catch (Throwable t) {
                            HookLog.e("AntiRecall resolver failed", t);
                        }
                    }
                }
        );
    }

    private static void installXmlParserHook(Context hostContext, HookContext context) throws Exception {
        System.loadLibrary("dexkit");

        String apkPath = context.loadPackageParam.appInfo != null
                ? context.loadPackageParam.appInfo.sourceDir
                : hostContext.getApplicationInfo().sourceDir;
        ClassLoader hostClassLoader = hostContext.getClassLoader();

        HookLog.i("AntiRecall resolving XML parser with DexKit");

        MethodData methodData;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            methodData = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mm.sdk.platformtools")
                            .matcher(
                                    MethodMatcher.create()
                                            .usingEqStrings("MicroMsg.SDK.XmlParser", "[ %s ]")
                            )
            ).singleOrThrow(() -> new IllegalStateException("WeChat XML parser match is not unique"));
        }

        Method parserMethod = methodData.getMethodInstance(hostClassLoader);
        HookLog.i("AntiRecall XML parser resolved: " + parserMethod);

        if (!PARSER_HOOKED.compareAndSet(false, true)) {
            return;
        }

        XposedBridge.hookMethod(parserMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    blockRecallIfNeeded(param);
                } catch (Throwable t) {
                    // Never let an anti-recall parsing failure break WeChat.
                    HookLog.e("AntiRecall parse handler failed", t);
                }
            }
        });

        HookLog.i("AntiRecall active");
    }

    @SuppressWarnings("unchecked")
    private static void blockRecallIfNeeded(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length < 2) {
            return;
        }

        Object xmlArg = param.args[0];
        Object rootArg = param.args[1];
        if (!(xmlArg instanceof String) || !(rootArg instanceof String)) {
            return;
        }

        String xmlContent = (String) xmlArg;
        String rootTag = (String) rootArg;
        if (!"sysmsg".equals(rootTag) || !xmlContent.contains("revokemsg")) {
            return;
        }

        Object resultObject = param.getResult();
        if (!(resultObject instanceof Map)) {
            return;
        }

        Map<Object, Object> result = (Map<Object, Object>) resultObject;
        if (!"revokemsg".equals(result.get(TYPE_KEY))) {
            return;
        }

        // Keep the parsed data available, but prevent WeChat's normal local
        // revoke handler from recognizing this map as a revoke event.
        result.put(TYPE_KEY, null);
        HookLog.i("AntiRecall blocked one local revoke event");
    }
}
