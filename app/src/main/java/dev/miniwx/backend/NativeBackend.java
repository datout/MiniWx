package dev.miniwx.backend;

/**
 * Optional native backend facade.
 *
 * <p>The backend is not bundled or loaded in 0.3. Keeping this facade in the
 * app module lets future features depend on a stable API instead of talking to
 * Zygisk/JNI directly.</p>
 */
public final class NativeBackend {
    private NativeBackend() {}

    public static boolean isAvailable() {
        return false;
    }

    public static boolean isZygiskAvailable() {
        return false;
    }
}
