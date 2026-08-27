package dev.miniwx.core;

import java.util.List;
import java.util.Set;

public final class HookManager {
    private final List<HookItem> items;

    public HookManager(List<HookItem> items) {
        this.items = items;
    }

    public void installAll(HookContext context) {
        HookLog.i("runtime capabilities=" + context.capabilities());

        for (HookItem item : items) {
            if (!item.enabled()) {
                HookLog.i("skip disabled hook: " + item.name());
                continue;
            }

            Set<RuntimeCapability> required = item.requiredCapabilities();
            if (!context.supportsAll(required)) {
                HookLog.i("skip unsupported hook: " + item.name()
                        + ", requires=" + required);
                continue;
            }

            try {
                item.install(context);
                HookLog.i("installed: " + item.name());
            } catch (Throwable t) {
                // One broken hook must not stop the rest of the module.
                HookLog.e("failed to install " + item.name(), t);
            }
        }
    }
}
