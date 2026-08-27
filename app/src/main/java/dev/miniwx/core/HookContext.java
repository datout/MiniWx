package dev.miniwx.core;

import java.util.EnumSet;
import java.util.Set;

import dev.miniwx.backend.NativeBackend;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookContext {
    public final XC_LoadPackage.LoadPackageParam loadPackageParam;
    public final ClassLoader classLoader;
    private final Set<RuntimeCapability> capabilities;

    public HookContext(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        this.loadPackageParam = loadPackageParam;
        this.classLoader = loadPackageParam.classLoader;

        EnumSet<RuntimeCapability> detected = EnumSet.of(RuntimeCapability.JAVA_HOOK);
        if (!isMainProcess()) {
            detected.add(RuntimeCapability.SUB_PROCESS);
        }
        if (NativeBackend.isAvailable()) {
            detected.add(RuntimeCapability.NATIVE_HOOK);
        }
        if (NativeBackend.isZygiskAvailable()) {
            detected.add(RuntimeCapability.ZYGISK);
        }
        this.capabilities = Set.copyOf(detected);
    }

    public boolean isMainProcess() {
        return loadPackageParam.packageName.equals(loadPackageParam.processName);
    }

    public boolean supportsAll(Set<RuntimeCapability> required) {
        return capabilities.containsAll(required);
    }

    public Set<RuntimeCapability> capabilities() {
        return capabilities;
    }
}
