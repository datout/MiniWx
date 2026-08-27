package dev.miniwx.config;

import android.content.Context;
import android.os.Bundle;

import dev.miniwx.core.HookLog;

/** Reads MiniWx settings from the module provider while code runs inside WeChat. */
public final class ModuleConfigClient {
    private ModuleConfigClient() {}

    public static boolean getBoolean(Context hostContext, String key) {
        try {
            Bundle result = hostContext.getContentResolver().call(
                    SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_BOOL,
                    key,
                    null
            );
            return result != null
                    ? result.getBoolean("value", FeatureFlags.defaultValue(key))
                    : FeatureFlags.defaultValue(key);
        } catch (Throwable t) {
            HookLog.e("config read failed for " + key, t);
            return FeatureFlags.defaultValue(key);
        }
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
}
