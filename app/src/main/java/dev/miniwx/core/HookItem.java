package dev.miniwx.core;

import java.util.Collections;
import java.util.Set;

public interface HookItem {
    String name();
    boolean enabled();
    void install(HookContext context) throws Throwable;

    /**
     * Capabilities required by this feature. Normal Java hooks need only
     * JAVA_HOOK; native/Zygisk features can opt in later without changing the
     * core loader contract.
     */
    default Set<RuntimeCapability> requiredCapabilities() {
        return Collections.singleton(RuntimeCapability.JAVA_HOOK);
    }
}
