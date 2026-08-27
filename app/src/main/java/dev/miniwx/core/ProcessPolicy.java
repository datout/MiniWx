package dev.miniwx.core;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Central place for deciding which WeChat processes MiniWx enters. */
public final class ProcessPolicy {
    private ProcessPolicy() {}

    public static boolean shouldLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        // 0.3 stays deliberately conservative: only the WeChat main process.
        // Future native/Zygisk features can extend this policy in one place.
        return lpparam.packageName.equals(lpparam.processName);
    }
}
