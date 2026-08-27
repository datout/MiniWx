package dev.miniwx.wechat;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

/**
 * Resolves WeChat's already-open WCDB handle from MMKernel/CoreStorage and exposes
 * narrowly-scoped read-only message queries for local features such as anti-recall.
 *
 * MiniWx intentionally does not open or decrypt the database itself; it reuses the
 * database object already owned by the running WeChat process.
 */
public final class WeChatDatabaseApi implements HookItem {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean STORAGE_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean READY_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean QUERY_ERROR_LOGGED = new AtomicBoolean(false);

    private static volatile Object database;
    private static volatile Method rawQueryMethod;
    private static volatile Context hostContext;

    @Override public String name() { return "WeChatDatabase"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                hostContext = (Context) param.args[0];
                if (!STARTED.compareAndSet(false, true)) return;
                Context host = hostContext;
                HookResolveExecutor.submit("WeChatDatabase", () -> resolve(host, context));
            }
        });
    }

    private static void resolve(Context host, HookContext hookContext) throws Exception {
        System.loadLibrary("dexkit");
        String apkPath = hookContext.loadPackageParam.appInfo != null
                ? hookContext.loadPackageParam.appInfo.sourceDir
                : host.getApplicationInfo().sourceDir;

        MethodData storageData;
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            storageData = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .modifiers(Modifier.PUBLIC | Modifier.STATIC)
                                    .paramCount(0)
                                    .usingEqStrings("mCoreStorage not initialized!")
                    )
            ).singleOrThrow(() -> new IllegalStateException("MMKernel storage getter match is not unique"));
        }

        Method getStorage = storageData.getMethodInstance(host.getClassLoader());
        getStorage.setAccessible(true);
        HookLog.i("WeChatDatabase storage getter resolved: " + getStorage);

        if (STORAGE_HOOKED.compareAndSet(false, true)) {
            XposedBridge.hookMethod(getStorage, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    tryInitialize(param.getResult());
                }
            });
        }

        // MMKernel may already be initialized before the background DexKit resolver finishes.
        // Try the getter once now; if it is not ready yet, the after-hook above will catch the
        // next successful call without blocking WeChat startup.
        try {
            Object storage = getStorage.invoke(null);
            tryInitialize(storage);
        } catch (Throwable t) {
            HookLog.i("WeChatDatabase waiting for CoreStorage initialization");
        }
    }

    private static void tryInitialize(Object storage) {
        if (storage == null || isReady()) return;
        try {
            DatabaseHandle handle = locateDatabase(storage);
            if (handle == null) return;

            database = handle.database();
            rawQueryMethod = handle.rawQuery();
            if (READY_LOGGED.compareAndSet(false, true)) {
                HookLog.i("WeChatDatabase ready: " + database.getClass().getName()
                        + " via " + rawQueryMethod);
            }
            Context context = hostContext;
            if (context != null) {
                ModuleConfigClient.reportHookStatus(
                        context,
                        "WeChatDatabase",
                        "正常",
                        "微信本地 message 数据库已就绪"
                );
            }
        } catch (Throwable t) {
            HookLog.e("WeChatDatabase initialization failed", t);
        }
    }

    private static DatabaseHandle locateDatabase(Object storage) throws IllegalAccessException {
        DatabaseHandle direct = asDatabase(storage);
        if (direct != null) return direct;

        for (Object candidate : directObjectFields(storage)) {
            DatabaseHandle handle = asDatabase(candidate);
            if (handle != null) return handle;

            handle = databaseFromGetter(candidate);
            if (handle != null) return handle;
        }
        return null;
    }

    private static Set<Object> directObjectFields(Object target) throws IllegalAccessException {
        Set<Object> values = new LinkedHashSet<>();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) values.add(value);
                } catch (Throwable ignored) {
                }
            }
        }
        return values;
    }

    private static DatabaseHandle databaseFromGetter(Object wrapper) {
        if (wrapper == null) return null;
        for (Method method : allMethods(wrapper.getClass())) {
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) continue;
            Class<?> returnType = method.getReturnType();
            if (!looksLikeDatabaseType(returnType)) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(wrapper);
                DatabaseHandle handle = asDatabase(value);
                if (handle != null) return handle;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static DatabaseHandle asDatabase(Object value) {
        if (value == null) return null;
        Method raw = findRawQueryMethod(value.getClass());
        return raw != null ? new DatabaseHandle(value, raw) : null;
    }

    private static boolean looksLikeDatabaseType(Class<?> type) {
        if (type == null || type == void.class) return false;
        String name = type.getName();
        if ("com.tencent.wcdb.database.SQLiteDatabase".equals(name)) return true;
        return findRawQueryMethod(type) != null;
    }

    private static Method findRawQueryMethod(Class<?> type) {
        if (type == null) return null;
        for (Method method : allMethods(type)) {
            if (!"rawQuery".equals(method.getName()) || method.getParameterCount() != 2) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params[0] != String.class || !params[1].isArray()) continue;
            try {
                method.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return method;
        }
        return null;
    }

    private static Set<Method> allMethods(Class<?> type) {
        Set<Method> methods = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Method method : c.getDeclaredMethods()) methods.add(method);
            } catch (Throwable ignored) {
            }
        }
        try {
            for (Method method : type.getMethods()) methods.add(method);
        } catch (Throwable ignored) {
        }
        return methods;
    }

    public static boolean isReady() {
        return database != null && rawQueryMethod != null;
    }

    /** Returns the original persisted message row identified by WeChat's msgSvrId. */
    public static MessageRecord findMessageByServerId(long serverId) {
        if (serverId == 0L || !isReady()) return null;
        Cursor cursor = null;
        try {
            Object queryArgs = createBindArgs(rawQueryMethod.getParameterTypes()[1], Long.toString(serverId));
            Object result = rawQueryMethod.invoke(
                    database,
                    "SELECT type,content,talker,createTime,msgId,msgSvrId,isSend FROM message WHERE msgSvrId = ? LIMIT 1",
                    queryArgs
            );
            if (!(result instanceof Cursor)) return null;
            cursor = (Cursor) result;
            if (!cursor.moveToFirst()) return null;

            return new MessageRecord(
                    getLong(cursor, "msgId"),
                    getLong(cursor, "msgSvrId"),
                    getString(cursor, "talker"),
                    getString(cursor, "content"),
                    getInt(cursor, "type"),
                    getLong(cursor, "createTime"),
                    getInt(cursor, "isSend")
            );
        } catch (Throwable t) {
            if (QUERY_ERROR_LOGGED.compareAndSet(false, true)) {
                HookLog.e("WeChatDatabase message query failed", t);
            }
            return null;
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static Object createBindArgs(Class<?> arrayType, String value) {
        Class<?> component = arrayType.getComponentType();
        if (component == null) throw new IllegalArgumentException("rawQuery bind parameter is not an array");
        Object array = Array.newInstance(component, 1);
        Array.set(array, 0, value);
        return array;
    }

    private static int getInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getInt(index) : 0;
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getLong(index) : 0L;
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : "";
    }

    private record DatabaseHandle(Object database, Method rawQuery) {}

    public record MessageRecord(
            long msgId,
            long serverId,
            String talker,
            String content,
            int typeCode,
            long createTime,
            int isSend
    ) {
        public boolean selfSender() { return isSend != 0; }
    }
}
