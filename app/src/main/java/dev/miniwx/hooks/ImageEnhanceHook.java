package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.wechat.ReflectionUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Automatically clicks WeChat's visible “查看原图 / 原视频” button in gallery UI. */
public final class ImageEnhanceHook implements HookItem {
    private static final String GALLERY_CLASS = "com.tencent.mm.ui.chatting.gallery.ImageGalleryUI";
    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static volatile Context hostContext;

    @Override public String name() { return "ImageEnhance"; }
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
                            resolveGalleryHooks(hostContext, context);
                        } catch (Throwable t) {
                            HookLog.e("ImageEnhance resolver failed", t);
                        }
                    }
                }
        );
    }

    private static void resolveGalleryHooks(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir
                : host.getApplicationInfo().sourceDir;
        Set<Method> methods = new HashSet<>();

        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            MethodData image = firstMatch(bridge, "setHdImageActionDownloadable");
            if (image == null) image = firstMatch(bridge, "setImageHdImgBtnVisibility");
            if (image != null) methods.add(image.getMethodInstance(host.getClassLoader()));

            MethodData video = firstMatch(bridge, "checkNeedShowOriginVideoBtn");
            if (video != null) methods.add(video.getMethodInstance(host.getClassLoader()));
        }

        if (methods.isEmpty()) {
            throw new IllegalStateException("no ImageGalleryUI original-media hook point found");
        }

        for (Method method : methods) {
            HookLog.i("ImageEnhance resolved: " + method);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    tryAutoClick(param.thisObject);
                }
            });
        }
        HookLog.i("ImageEnhance active, hooks=" + methods.size());
    }

    private static MethodData firstMatch(DexKitBridge bridge, String marker) {
        try {
            return bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass(GALLERY_CLASS)
                                    .usingEqStrings(marker)
                    )
            ).singleOrThrow(() -> new IllegalStateException("match is not unique: " + marker));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void tryAutoClick(Object galleryUi) {
        Context context = hostContext;
        if (context == null || !ModuleConfigClient.getBoolean(context, FeatureFlags.IMAGE_ENHANCE)) return;
        if (galleryUi == null) return;

        List<Button> buttons = ReflectionUtils.allFieldValues(galleryUi, Button.class);
        for (Button button : buttons) {
            if (button == null || button.getVisibility() != View.VISIBLE || !button.isShown()) continue;
            CharSequence raw = button.getText();
            String text = raw != null ? raw.toString().trim().toLowerCase(Locale.ROOT) : "";
            if (!looksLikeOriginalButton(text)) continue;
            button.post(() -> {
                try {
                    if (button.getVisibility() == View.VISIBLE && button.isShown()) button.performClick();
                } catch (Throwable t) {
                    HookLog.e("ImageEnhance click failed", t);
                }
            });
        }
    }

    private static boolean looksLikeOriginalButton(String text) {
        return text.contains("查看原图")
                || text.contains("查看原视频")
                || text.contains("full image")
                || text.contains("original quality");
    }
}
