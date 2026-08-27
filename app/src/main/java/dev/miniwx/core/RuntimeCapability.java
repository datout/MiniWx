package dev.miniwx.core;

/**
 * Runtime capabilities that a feature may require.
 *
 * <p>0.5 only exposes JAVA_HOOK. Native/Zygisk capabilities are intentionally
 * reserved for future optional backends so normal features never depend on
 * them.</p>
 */
public enum RuntimeCapability {
    JAVA_HOOK,
    NATIVE_HOOK,
    ZYGISK,
    SUB_PROCESS
}
