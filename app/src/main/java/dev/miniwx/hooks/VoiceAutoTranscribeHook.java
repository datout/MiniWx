package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookResolveExecutor;
import dev.miniwx.wechat.MessageInfo;
import dev.miniwx.wechat.MessageViewApi;
import dev.miniwx.wechat.ReflectionUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/**
 * Uses WeChat's own TransformComponent to request voice-to-text for incoming voice messages.
 * The flow mirrors WCXLC's AutoSpeechToText but keeps the resolver self-contained.
 */
public final class VoiceAutoTranscribeHook implements HookItem {
    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static volatile Class<?> chattingContextClass;
    private static volatile Class<?> transformComponentClass;
    private static volatile Class<?> apiManagerClass;
    private static volatile Method apiManagerGetApi;
    private static volatile Class<?> messageClass;
    private static volatile Class<?> voiceLogicClass;
    private static volatile Method markVoicePlayedMethod;

    private static final Map<Long, Boolean> PROCESSED = Collections.synchronizedMap(
            new LinkedHashMap<Long, Boolean>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > 768;
                }
            }
    );

    @Override public String name() { return "VoiceAutoTranscribe"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        MessageViewApi.addListener(this::onMessageBind);

        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!RESOLVE_STARTED.compareAndSet(false, true)) return;
                        Context host = (Context) param.args[0];
                        HookResolveExecutor.submit("VoiceAutoTranscribe", () -> resolve(host, context));
                    }
                }
        );
    }

    private static void resolve(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir
                : host.getApplicationInfo().sourceDir;
        ClassLoader loader = host.getClassLoader();

        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            MethodData chattingContextProbe = bridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create()
                            .usingEqStrings("MicroMsg.ChattingContext", "[notifyDataSetChange]"))
            ).singleOrThrow(() -> new IllegalStateException("ChattingContext probe is not unique"));

            MethodData transformProbe = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mm.ui.chatting.component")
                            .matcher(MethodMatcher.create()
                                    .usingEqStrings("MicroMsg.TransformComponent", "[onChattingPause]"))
            ).singleOrThrow(() -> new IllegalStateException("TransformComponent probe is not unique"));

            MethodData getApiData = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mm.ui.chatting.manager")
                            .matcher(MethodMatcher.create().usingEqStrings("[get] ", " is not a interface!"))
            ).singleOrThrow(() -> new IllegalStateException("ApiManager.get is not unique"));

            MethodData msgInfoProbe = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages("com.tencent.mm.storage")
                            .matcher(MethodMatcher.create()
                                    .usingEqStrings("MicroMsg.MsgInfo", "[parseNewXmlSysMsg]"))
            ).singleOrThrow(() -> new IllegalStateException("MsgInfo probe is not unique"));

            MethodData voiceLogicProbe = bridge.findMethod(
                    FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .usingEqStrings("MicroMsg.VoiceLogic", "startRecord insert voicestg success"))
            ).singleOrThrow(() -> new IllegalStateException("VoiceLogic probe is not unique"));

            chattingContextClass = chattingContextProbe.getMethodInstance(loader).getDeclaringClass();
            transformComponentClass = transformProbe.getMethodInstance(loader).getDeclaringClass();
            apiManagerGetApi = getApiData.getMethodInstance(loader);
            apiManagerGetApi.setAccessible(true);
            apiManagerClass = apiManagerGetApi.getDeclaringClass();
            messageClass = msgInfoProbe.getMethodInstance(loader).getDeclaringClass();
            voiceLogicClass = voiceLogicProbe.getMethodInstance(loader).getDeclaringClass();
            markVoicePlayedMethod = findMarkVoicePlayedMethod(voiceLogicClass, messageClass);
        }

        HookLog.i("VoiceAutoTranscribe resolved: chattingContext=" + chattingContextClass
                + ", transform=" + transformComponentClass
                + ", apiManager=" + apiManagerClass
                + ", markPlayed=" + markVoicePlayedMethod);
    }

    private static void onMessageBind(View root, MessageInfo message, Object chattingItem) {
        Context context = root.getContext();
        if (context == null) return;
        if (!ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_ENHANCE)
                || !ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_AUTO_TRANSCRIBE)) return;
        if (!message.isVoice() || message.isSelfSender()) return;
        if (!message.transContent().trim().isEmpty()) return;
        if (chattingContextClass == null || transformComponentClass == null || apiManagerGetApi == null) return;

        long id = message.id();
        synchronized (PROCESSED) {
            if (Boolean.TRUE.equals(PROCESSED.get(id))) return;
        }

        try {
            Object chattingContext = ReflectionUtils.firstFieldValueRaw(chattingItem, chattingContextClass);
            if (chattingContext == null) return;
            Object apiManager = ReflectionUtils.firstFieldValueRaw(chattingContext, apiManagerClass);
            if (apiManager == null) return;

            Class<?>[] interfaces = transformComponentClass.getInterfaces();
            if (interfaces.length == 0) return;
            Object transformApi = apiManagerGetApi.invoke(apiManager, interfaces[0]);
            if (transformApi == null) return;

            Method stateMethod = findVoiceTransformStateMethod(transformApi.getClass());
            if (stateMethod != null) {
                Object state = stateMethod.invoke(transformApi, id);
                if (state != null && !"NoTransform".equals(String.valueOf(state))) return;
            }

            Method transformMethod = findTransformMethod(transformApi.getClass(), message.instance().getClass());
            if (transformMethod == null) {
                HookLog.i("VoiceAutoTranscribe: transform method not found on " + transformApi.getClass());
                return;
            }

            synchronized (PROCESSED) { PROCESSED.put(id, Boolean.TRUE); }
            markVoicePlayed(message.instance());
            try {
                transformMethod.invoke(transformApi, message.instance(), false, -1, 0);
            } catch (InvocationTargetException expected) {
                // Current WeChat builds may throw after the transform request was already dispatched.
                // WCXLC explicitly tolerates this path as it does not affect the conversion.
                Throwable cause = expected.getCause();
                if (!(cause instanceof NullPointerException)) {
                    HookLog.e("VoiceAutoTranscribe invoke returned exception", cause != null ? cause : expected);
                }
            }
            HookLog.i("VoiceAutoTranscribe requested for msgId=" + id);
        } catch (Throwable t) {
            HookLog.e("VoiceAutoTranscribe bind failed", t);
        }
    }

    private static Method findVoiceTransformStateMethod(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 1 || (p[0] != long.class && p[0] != Long.class)) continue;
                if (!method.getReturnType().getName().startsWith("com.tencent.mm.ui.chatting.viewitems")) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findTransformMethod(Class<?> type, Class<?> messageClass) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 4 || method.getReturnType() != void.class) continue;
                if (!p[0].isAssignableFrom(messageClass)) continue;
                if (p[1] != boolean.class || p[2] != int.class || p[3] != int.class) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
    private static Method findMarkVoicePlayedMethod(Class<?> voiceLogic, Class<?> msgClass) {
        if (voiceLogic == null || msgClass == null) return null;
        for (Class<?> c = voiceLogic; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) continue;
                if (p.length != 1 || !p[0].isAssignableFrom(msgClass)) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static void markVoicePlayed(Object message) {
        Method method = markVoicePlayedMethod;
        if (method == null || message == null) return;
        try {
            method.invoke(null, message);
        } catch (Throwable t) {
            HookLog.e("VoiceAutoTranscribe mark played failed", t);
        }
    }

}
