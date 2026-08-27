package dev.miniwx.hooks;

import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
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
import dev.miniwx.wechat.MessageViewUi;
import dev.miniwx.wechat.ReflectionUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Displays group-owner / administrator badges before sender names. */
public final class GroupRoleHook implements HookItem {
    private static final int ROLE_OWNER = 1;
    private static final int ROLE_ADMIN = 2;
    private static final int ROLE_MEMBER = 3;
    private static final int ADMIN_FLAG = 2048;

    private static final AtomicBoolean RESOLVE_STARTED = new AtomicBoolean(false);
    private static volatile Class<?> mmKernelClass;
    private static volatile Class<?> chatroomServiceClass;
    private static volatile Class<?> chatroomStorageClass;
    private static volatile Class<?> chatroomMemberClass;
    private static volatile Method getChatroomDataMethod;
    private static volatile Object chatroomStorage;
    private static volatile Method getGroupMethod;

    private static final Map<String, Integer> ROLE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Integer>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 1024;
                }
            }
    );

    @Override public String name() { return "GroupRoleBadges"; }
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
                        HookResolveExecutor.submit("GroupRole", () -> resolve(host, context));
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
            MethodData kernelProbe = exactMethod(bridge, null,
                    "MicroMsg.MMKernel", "Kernel not null, has initialized.");
            MethodData serviceProbe = exactMethod(bridge, null,
                    "MicroMsg.ChatroomService", "[isEnableRoomManager]");
            MethodData storageCount = optionalExactMethod(bridge, "com.tencent.mm.storage",
                    "MicroMsg.ChatroomStorage", "[getMemberCount] cost:%sms");
            if (storageCount == null) {
                storageCount = exactMethod(bridge, "com.tencent.mm.storage",
                        "MicroMsg.ChatroomStorage", "[getMemberCount] init field_memberCount! username:%s count:%s");
            }
            MethodData memberProbe = exactMethod(bridge, "com.tencent.mm.storage",
                    "MicroMsg.ChatRoomMember", "service is null");
            MethodData getMemberData = exactMethod(bridge, null,
                    "MicroMsg.ChatRoomMember", "getChatroomData hashMap is null!");

            mmKernelClass = kernelProbe.getMethodInstance(loader).getDeclaringClass();
            chatroomServiceClass = serviceProbe.getMethodInstance(loader).getDeclaringClass();
            chatroomStorageClass = storageCount.getMethodInstance(loader).getDeclaringClass();
            chatroomMemberClass = memberProbe.getMethodInstance(loader).getDeclaringClass();
            getChatroomDataMethod = getMemberData.getMethodInstance(loader);
            getChatroomDataMethod.setAccessible(true);
        }
        HookLog.i("GroupRole resolved: service=" + chatroomServiceClass
                + ", storage=" + chatroomStorageClass
                + ", member=" + chatroomMemberClass);
    }

    private static MethodData exactMethod(DexKitBridge bridge, String pkg, String... strings) {
        FindMethod query = FindMethod.create();
        if (pkg != null) query.searchPackages(pkg);
        query.matcher(MethodMatcher.create().usingEqStrings(strings));
        return bridge.findMethod(query).singleOrThrow(
                () -> new IllegalStateException("DexKit method is not unique for " + String.join(" | ", strings)));
    }

    private static MethodData optionalExactMethod(DexKitBridge bridge, String pkg, String... strings) {
        try {
            return exactMethod(bridge, pkg, strings);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void onMessageBind(View root, MessageInfo message, Object chattingItem) {
        Context context = root.getContext();
        if (context == null) return;
        if (!ModuleConfigClient.getBoolean(context, FeatureFlags.GROUP_ENHANCE)
                || !ModuleConfigClient.getBoolean(context, FeatureFlags.GROUP_ROLE_BADGE)) return;
        if (!message.isGroupChat() || message.isSelfSender()) return;
        String sender = message.senderWxId();
        if (sender == null || sender.trim().isEmpty()) return;
        if (getChatroomDataMethod == null || chatroomStorageClass == null) return;

        try {
            int role = resolveRole(message.talker(), sender);
            if (role == ROLE_MEMBER
                    && !ModuleConfigClient.getBoolean(context, FeatureFlags.GROUP_SHOW_MEMBER)) return;

            TextView userView = MessageViewUi.findTextField(root.getTag(), "userTV");
            if (userView == null) return;
            CharSequence current = stripOldPrefix(userView.getText());
            String label = role == ROLE_OWNER ? "群主" : role == ROLE_ADMIN ? "管理员" : "成员";
            int color = role == ROLE_OWNER ? 0xFFFFC107 : role == ROLE_ADMIN ? 0xFF2196F3 : 0xFF9E9E9E;

            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append(label).append(" ").append(current);
            float density = userView.getResources().getDisplayMetrics().density;
            text.setSpan(new RoleSpan(color, 0xFFFFFFFF, 6f * density, 4f * density),
                    0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            userView.setText(text);
        } catch (Throwable t) {
            HookLog.e("GroupRole bind failed", t);
        }
    }

    private static CharSequence stripOldPrefix(CharSequence value) {
        String text = value == null ? "" : value.toString();
        for (String prefix : new String[]{"群主 ", "管理员 ", "成员 "}) {
            if (text.startsWith(prefix)) return text.substring(prefix.length());
        }
        return value == null ? "" : value;
    }

    private static int resolveRole(String groupId, String sender) throws Exception {
        String cacheKey = groupId + '\u0000' + sender;
        Integer cached = ROLE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        Object group = getGroup(groupId);
        if (group == null) return ROLE_MEMBER;
        Object ownerValue = ReflectionUtils.getField(group, "field_roomowner");
        if (sender.equals(ownerValue)) {
            ROLE_CACHE.put(cacheKey, ROLE_OWNER);
            return ROLE_OWNER;
        }

        Object memberData = getChatroomDataMethod.invoke(group, sender);
        Integer flags = ReflectionUtils.firstIntFieldValue(memberData);
        int role = flags != null && (flags & ADMIN_FLAG) != 0 ? ROLE_ADMIN : ROLE_MEMBER;
        ROLE_CACHE.put(cacheKey, role);
        return role;
    }

    private static Object getGroup(String groupId) throws Exception {
        Object storage = getChatroomStorage();
        if (storage == null) return null;
        Method method = getGroupMethod;
        if (method == null) {
            for (Class<?> c = storage.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method candidate : c.getDeclaredMethods()) {
                    Class<?>[] p = candidate.getParameterTypes();
                    if (p.length != 1 || p[0] != String.class) continue;
                    if (!chatroomMemberClass.isAssignableFrom(candidate.getReturnType())) continue;
                    candidate.setAccessible(true);
                    getGroupMethod = candidate;
                    method = candidate;
                    break;
                }
                if (method != null) break;
            }
        }
        return method != null ? method.invoke(storage, groupId) : null;
    }

    private static Object getChatroomStorage() {
        Object cached = chatroomStorage;
        if (cached != null) return cached;
        try {
            Object service = getChatroomService();
            if (service == null) return null;
            for (Class<?> c = service.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (method.getParameterCount() != 0) continue;
                    if (!chatroomStorageClass.isAssignableFrom(method.getReturnType())) continue;
                    method.setAccessible(true);
                    Object value = method.invoke(service);
                    if (value != null) {
                        chatroomStorage = value;
                        return value;
                    }
                }
            }
        } catch (Throwable t) {
            HookLog.e("GroupRole get storage failed", t);
        }
        return null;
    }

    private static Object getChatroomService() {
        if (mmKernelClass == null || chatroomServiceClass == null) return null;
        Class<?>[] serviceInterfaces = chatroomServiceClass.getInterfaces();
        if (serviceInterfaces.length == 0) return null;

        for (Class<?> c = mmKernelClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 1 || p[0] != Class.class) continue;
                try {
                    method.setAccessible(true);
                    for (Class<?> serviceInterface : serviceInterfaces) {
                        Object value = method.invoke(null, serviceInterface);
                        if (value != null && chatroomServiceClass.isInstance(value)) return value;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static final class RoleSpan extends ReplacementSpan {
        private final int backgroundColor;
        private final int textColor;
        private final float cornerRadius;
        private final float padding;

        RoleSpan(int backgroundColor, int textColor, float cornerRadius, float padding) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
            this.cornerRadius = cornerRadius;
            this.padding = padding;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(paint.measureText(text, start, end) + padding * 2f);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            float width = paint.measureText(text, start, end);
            int oldColor = paint.getColor();
            Paint.Style oldStyle = paint.getStyle();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(backgroundColor);
            canvas.drawRoundRect(new RectF(x, top, x + width + padding * 2f, bottom),
                    cornerRadius, cornerRadius, paint);
            paint.setColor(textColor);
            canvas.drawText(text, start, end, x + padding, y, paint);
            paint.setColor(oldColor);
            paint.setStyle(oldStyle);
        }
    }
}
