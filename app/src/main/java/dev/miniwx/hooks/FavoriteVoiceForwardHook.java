package dev.miniwx.hooks;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookResolveExecutor;
import dev.miniwx.wechat.ReflectionUtils;
import dev.miniwx.wechat.WeChatVoiceApi;
import dev.miniwx.wechat.proto.FavoriteVoiceProto;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Allows a single voice favorite to pass WeChat's forward gate and re-send as a voice message. */
public final class FavoriteVoiceForwardHook implements HookItem {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static volatile Context hostContext;
    private static volatile String currentConversation = "";

    @Override public String name() { return "FavoriteVoiceForward"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                hostContext = (Context) param.args[0];
                if (!STARTED.compareAndSet(false, true)) return;
                HookResolveExecutor.submit("FavoriteVoiceForward", () -> resolveAndHook(hostContext, context));
            }
        });
    }

    private static void resolveAndHook(Context host, HookContext hookContext) throws Exception {
        WeChatVoiceApi.ensureResolved(host, hookContext);
        ClassLoader loader = host.getClassLoader();
        Class<?> favIndex = XposedHelpers.findClass("com.tencent.mm.plugin.fav.ui.FavoriteIndexUI", loader);
        Class<?> favSelect = XposedHelpers.findClass("com.tencent.mm.plugin.fav.ui.FavSelectUI", loader);
        Class<?> chatFooter = XposedHelpers.findClass("com.tencent.mm.pluginsdk.ui.chat.ChatFooter", loader);
        if (!HOOKED.compareAndSet(false, true)) return;

        hookCurrentConversation(chatFooter);
        int selectCount = hookChatFavoritePicker(favSelect);
        int gateCount = 0;
        int sendCount = 0;
        for (Method method : favIndex.getDeclaredMethods()) {
            method.setAccessible(true);
            Class<?>[] p = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == boolean.class
                    && p.length >= 4 && p.length <= 5
                    && p[0] == List.class && p[1] == Context.class
                    && p[2] == DialogInterface.OnClickListener.class
                    && trailingBooleans(p, 3)) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Context context = hostContext;
                        if (!featureEnabled(context)) return;
                        Object favorite = singleFavorite(param.args[0]);
                        if (favorite != null && favoriteVoice(context, favorite) != null) {
                            param.setResult(true);
                        }
                    }
                });
                gateCount++;
                continue;
            }

            if (method.getReturnType() == void.class && p.length == 4
                    && p[0] == List.class && p[1] == String.class
                    && p[2] == String.class && p[3] == boolean.class) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Context context = hostContext;
                        if (!featureEnabled(context)) return;
                        Object favorite = singleFavorite(param.args[0]);
                        FavoriteVoice voice = favorite != null ? favoriteVoice(context, favorite) : null;
                        if (voice == null) return;

                        String recipientsRaw = param.args[2] instanceof String ? (String) param.args[2] : "";
                        List<String> recipients = parseRecipients(recipientsRaw);
                        if (recipients.isEmpty()) return;

                        int success = 0;
                        for (String wxid : recipients) {
                            if (WeChatVoiceApi.sendVoice(wxid, voice.filePath, voice.durationMs)) success++;
                        }
                        if (success <= 0) {
                            Toast.makeText(context, "收藏语音转发失败，请查看 MiniWx Hook 状态/LSPosed 日志", Toast.LENGTH_LONG).show();
                            return;
                        }

                        param.setResult(null);
                        String customText = param.args[1] instanceof String ? ((String) param.args[1]).trim() : "";
                        String message = recipients.size() == 1
                                ? "收藏语音已转发"
                                : "收藏语音已转发 " + success + "/" + recipients.size() + " 个会话";
                        if (!customText.isEmpty()) message += "；附言请单独发送";
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }
                });
                sendCount++;
            }
        }
        if (gateCount == 0 || sendCount == 0) {
            throw new IllegalStateException("FavoriteIndexUI forward methods not found: gate=" + gateCount + ", send=" + sendCount);
        }
        HookLog.i("FavoriteVoiceForward active, picker=" + selectCount + ", gate=" + gateCount + ", send=" + sendCount);
    }

    private static void hookCurrentConversation(Class<?> chatFooter) {
        for (Method method : chatFooter.getDeclaredMethods()) {
            if (!"setUserName".equals(method.getName())) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p.length != 1 || p[0] != String.class) continue;
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0 && param.args[0] instanceof String value
                            && !value.trim().isEmpty()) {
                        currentConversation = value;
                    }
                }
            });
        }
    }

    private static int hookChatFavoritePicker(Class<?> favSelect) {
        int count = 0;
        for (Method method : favSelect.getDeclaredMethods()) {
            if (!"onItemClick".equals(method.getName())) continue;
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = hostContext;
                    if (!featureEnabled(context) || currentConversation.trim().isEmpty()) return;
                    if (param.args == null || param.args.length < 2 || !(param.args[1] instanceof View view)) return;
                    Object tag = view.getTag();
                    Object favorite = tag != null ? ReflectionUtils.getField(tag, "a") : null;
                    FavoriteVoice voice = favorite != null ? favoriteVoice(context, favorite) : null;
                    if (voice == null) return;
                    if (!WeChatVoiceApi.sendVoice(currentConversation, voice.filePath, voice.durationMs)) return;
                    param.setResult(null);
                    Toast.makeText(context, "收藏语音已发送", Toast.LENGTH_SHORT).show();
                    if (param.thisObject instanceof Activity activity) activity.finish();
                }
            });
            count++;
        }
        return count;
    }

    private static boolean featureEnabled(Context context) {
        return context != null
                && ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_ENHANCE)
                && ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_FAVORITE_FORWARD);
    }

    private static boolean trailingBooleans(Class<?>[] types, int start) {
        for (int i = start; i < types.length; i++) if (types[i] != boolean.class) return false;
        return true;
    }

    private static Object singleFavorite(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 1) return null;
        return list.get(0);
    }

    private static List<String> parseRecipients(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String item : raw.split(",")) {
            String value = item.trim();
            if (!value.isEmpty() && !out.contains(value)) out.add(value);
        }
        return out;
    }

    private static FavoriteVoice favoriteVoice(Context context, Object favorite) {
        try {
            Object type = ReflectionUtils.getField(favorite, "field_type");
            if (!(type instanceof Number) || ((Number) type).intValue() != 3) return null;
            Object proto = ReflectionUtils.getField(favorite, "field_favProto");
            if (proto == null) return null;
            Method getData = ReflectionUtils.findMethod(proto.getClass(), "getData");
            if (getData == null) return null;
            Object raw = getData.invoke(proto);
            if (!(raw instanceof byte[] bytes)) return null;
            FavoriteVoiceProto.VoiceInfo info = FavoriteVoiceProto.decode(bytes);
            if (info == null) return null;

            if (info.filePath() != null && !info.filePath().trim().isEmpty()) {
                File direct = new File(info.filePath());
                if (direct.isFile()) return new FavoriteVoice(direct.getAbsolutePath(), info.durationMs());
            }

            int defaultUin = context.getSharedPreferences("system_config_prefs", Context.MODE_PRIVATE)
                    .getInt("default_uin", 0);
            String accountDir = md5("mm" + defaultUin).toLowerCase(Locale.ROOT);
            int bucket = info.cacheName().hashCode() & 0xFF;
            String extension = info.cacheType() == null || info.cacheType().trim().isEmpty() ? "amr" : info.cacheType();
            File file = new File(new File(new File(new File(context.getDataDir(), "MicroMsg"), accountDir),
                    "favorite/" + bucket), info.cacheName() + "." + extension);
            return file.isFile() ? new FavoriteVoice(file.getAbsolutePath(), info.durationMs()) : null;
        } catch (Throwable t) {
            HookLog.e("favoriteVoice parse failed", t);
            return null;
        }
    }

    private static String md5(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        return out.toString();
    }

    private record FavoriteVoice(String filePath, int durationMs) {}
}
