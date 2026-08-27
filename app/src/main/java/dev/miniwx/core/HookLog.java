package dev.miniwx.core;

import de.robv.android.xposed.XposedBridge;

public final class HookLog {
    private static final String PREFIX = "[MiniWx] ";

    private HookLog() {}

    public static void i(String message) {
        XposedBridge.log(PREFIX + message);
    }

    public static void e(String message, Throwable throwable) {
        XposedBridge.log(PREFIX + message + ": " + throwable);
        XposedBridge.log(throwable);
    }
}
