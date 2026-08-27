package dev.miniwx.wechat;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookLog;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Small compatibility layer around WeChat's existing voice storage/send logic. */
public final class WeChatVoiceApi {
    private static final AtomicBoolean READY = new AtomicBoolean(false);
    private static Method voiceNameMethod;
    private static Method setVoiceMethod;
    private static Method getFullPathMethod;
    private static Class<?> sceneVoiceServiceClass;
    private static Method runVoiceServiceMethod;
    private static Method startRecvAndSendMethod;
    private static Class<?> mmKernelClass;

    private WeChatVoiceApi() {}

    public static boolean isReady() { return READY.get(); }

    public static synchronized void ensureResolved(Context host, HookContext hookContext) throws Exception {
        if (READY.get()) return;
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir : host.getApplicationInfo().sourceDir;
        ClassLoader loader = host.getClassLoader();

        MethodData voiceProbe;
        MethodData fullPath;
        MethodData sceneProbe;
        MethodData startMethod;
        MethodData runMethod = null;
        MethodData kernelProbe;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            voiceProbe = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings(
                            "MicroMsg.VoiceLogic", "startRecord insert voicestg success"
                    ))).singleOrThrow(() -> new IllegalStateException("VoiceLogic match is not unique"));
            fullPath = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings("getAmrFullPath cost: ")
            )).singleOrThrow(() -> new IllegalStateException("getAmrFullPath match is not unique"));
            sceneProbe = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings(
                            "MicroMsg.SceneVoiceService", "//voicetrymore", "getVoiceService %s"
                    ))).singleOrThrow(() -> new IllegalStateException("SceneVoiceService match is not unique"));
            startMethod = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings(
                            "MicroMsg.SceneVoiceService", "Start Recv[%s] :%s", "Start Send :"
                    ))).singleOrThrow(() -> new IllegalStateException("voice start method is not unique"));
            try {
                runMethod = bridge.findMethod(FindMethod.create().matcher(
                        MethodMatcher.create().usingEqStrings("MicroMsg.SceneVoiceService", "run() %s")
                )).singleOrThrow(() -> new IllegalStateException("voice run method is not unique"));
            } catch (Throwable ignored) {
                runMethod = null;
            }
            kernelProbe = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().usingEqStrings("MicroMsg.MMKernel", "Initialize skeleton")
            )).singleOrThrow(() -> new IllegalStateException("MMKernel match is not unique"));
        }

        Method voiceProbeMethod = voiceProbe.getMethodInstance(loader);
        Class<?> voiceLogic = voiceProbeMethod.getDeclaringClass();
        getFullPathMethod = fullPath.getMethodInstance(loader);
        getFullPathMethod.setAccessible(true);
        sceneVoiceServiceClass = sceneProbe.getMethodInstance(loader).getDeclaringClass();
        startRecvAndSendMethod = startMethod.getMethodInstance(loader);
        startRecvAndSendMethod.setAccessible(true);
        mmKernelClass = kernelProbe.getMethodInstance(loader).getDeclaringClass();

        for (Method method : voiceLogic.getDeclaredMethods()) {
            method.setAccessible(true);
            Class<?>[] p = method.getParameterTypes();
            if (voiceNameMethod == null && Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == String.class
                    && p.length == 2 && p[0] == String.class && p[1] == String.class) {
                voiceNameMethod = method;
            }
            if (setVoiceMethod == null && method.getReturnType() == boolean.class
                    && (p.length == 3 || p.length == 4)
                    && p[0] == String.class && p[1] == int.class && p[2] == int.class) {
                setVoiceMethod = method;
            }
        }
        if (voiceNameMethod == null || setVoiceMethod == null) {
            throw new IllegalStateException("VoiceLogic helper methods not found");
        }

        if (runMethod != null) {
            runVoiceServiceMethod = runMethod.getMethodInstance(loader);
            runVoiceServiceMethod.setAccessible(true);
        }

        READY.set(true);
        HookLog.i("WeChatVoiceApi ready: " + voiceLogic.getName());
    }

    public static String getVoiceFullPath(String encPath) throws Exception {
        if (!READY.get() || encPath == null || encPath.trim().isEmpty()) return null;
        Object receiver = receiverFor(getFullPathMethod);
        Object value = getFullPathMethod.invoke(receiver, null, encPath, true);
        return value != null ? String.valueOf(value) : null;
    }

    public static boolean sendVoice(String toUser, String sourcePath, int durationMs) {
        if (!READY.get() || toUser == null || toUser.trim().isEmpty() || sourcePath == null || sourcePath.trim().isEmpty()) return false;
        try {
            String partialPath = (String) voiceNameMethod.invoke(null, toUser, "amr_");
            String fullPath = getVoiceFullPath(partialPath);
            if (fullPath == null || fullPath.trim().isEmpty()) return false;
            Files.copy(new java.io.File(sourcePath).toPath(), new java.io.File(fullPath).toPath(), StandardCopyOption.REPLACE_EXISTING);

            int duration = Math.max(1, Math.min(durationMs, 60_000));
            Object setReceiver = receiverFor(setVoiceMethod);
            if (setVoiceMethod.getParameterCount() == 4) {
                setVoiceMethod.invoke(setReceiver, partialPath, duration, 0, null);
            } else {
                setVoiceMethod.invoke(setReceiver, partialPath, duration, 0);
            }
            Object service = getSceneVoiceService();
            if (service == null) throw new IllegalStateException("SceneVoiceService instance unavailable");
            if (runVoiceServiceMethod != null) {
                runVoiceServiceMethod.invoke(service);
            } else if (Modifier.isStatic(startRecvAndSendMethod.getModifiers())) {
                startRecvAndSendMethod.invoke(null, service);
            } else {
                startRecvAndSendMethod.invoke(receiverFor(startRecvAndSendMethod), service);
            }
            HookLog.i("voice sent to " + toUser + ", path=" + sourcePath);
            return true;
        } catch (Throwable t) {
            HookLog.e("sendVoice failed", t);
            return false;
        }
    }

    private static Object getSceneVoiceService() {
        for (Method method : sceneVoiceServiceClass.getDeclaredMethods()) {
            try {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
                if (!sceneVoiceServiceClass.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                Object value = method.invoke(null);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return staticSingleton(sceneVoiceServiceClass);
    }

    private static Object receiverFor(Method method) {
        if (Modifier.isStatic(method.getModifiers())) return null;
        Object singleton = staticSingleton(method.getDeclaringClass());
        if (singleton != null) return singleton;
        if (mmKernelClass != null) {
            for (Method kernel : mmKernelClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(kernel.getModifiers())) continue;
                Class<?>[] p = kernel.getParameterTypes();
                if (p.length != 1 || p[0] != Class.class) continue;
                try {
                    kernel.setAccessible(true);
                    Class<?> target = method.getDeclaringClass();
                    Object direct = kernel.invoke(null, target);
                    if (direct != null && target.isInstance(direct)) return direct;
                    for (Class<?> iface : target.getInterfaces()) {
                        Object value = kernel.invoke(null, iface);
                        if (value != null && target.isInstance(value)) return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object staticSingleton(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            try {
                if (!Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
