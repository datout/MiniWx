package dev.miniwx.config;

import android.content.Context;
import android.os.Bundle;

import java.util.concurrent.ConcurrentHashMap;

import dev.miniwx.core.HookLog;

/** Reads MiniWx settings from the module provider while code runs inside WeChat. */
public final class ModuleConfigClient {
    private static final long CACHE_TTL_MS = 1500L;
    private static final ConcurrentHashMap<String, CachedBoolean> BOOL_CACHE = new ConcurrentHashMap<>();

    private ModuleConfigClient() {}

    public static boolean getBoolean(Context hostContext, String key) {
        long now = System.currentTimeMillis();
        CachedBoolean cached = BOOL_CACHE.get(key);
        if (cached != null && now - cached.readAt < CACHE_TTL_MS) return cached.value;

        boolean value = FeatureFlags.defaultValue(key);
        try {
            Bundle result = hostContext.getContentResolver().call(
                    SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_BOOL,
                    key,
                    null
            );
            if (result != null) value = result.getBoolean("value", value);
        } catch (Throwable t) {
            HookLog.e("config read failed for " + key, t);
        }
        BOOL_CACHE.put(key, new CachedBoolean(value, now));
        return value;
    }

    public static void reportRuntime(Context hostContext, String version, String process) {
        try {
            Bundle extras = new Bundle();
            extras.putString("wechat_version", version);
            extras.putString("process", process);
            hostContext.getContentResolver().call(
                    SettingsProvider.URI,
                    SettingsProvider.METHOD_REPORT_RUNTIME,
                    null,
                    extras
            );
        } catch (Throwable t) {
            HookLog.e("runtime report failed", t);
        }
    }

    private static final class CachedBoolean {
        final boolean value;
        final long readAt;

        CachedBoolean(boolean value, long readAt) {
            this.value = value;
            this.readAt = readAt;
        }
    }
}
