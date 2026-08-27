package dev.miniwx;

import java.util.Arrays;

import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookManager;
import dev.miniwx.core.ProcessPolicy;
import dev.miniwx.hooks.AntiRecallHook;
import dev.miniwx.hooks.HostLifecycleHook;
import dev.miniwx.hooks.MessageSelectionLimitHook;
import dev.miniwx.hooks.FavoriteVoiceForwardHook;
import dev.miniwx.hooks.VoiceSaveHook;
import dev.miniwx.hooks.MessageSnapshotHook;
import dev.miniwx.hooks.ImageEnhanceHook;
import dev.miniwx.hooks.MessageEnhancementsHook;
import dev.miniwx.hooks.VoiceAutoTranscribeHook;
import dev.miniwx.hooks.NotificationEnhanceHook;
import dev.miniwx.hooks.GroupRoleHook;
import dev.miniwx.wechat.MessageViewApi;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class XposedEntry implements IXposedHookLoadPackage {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!WECHAT_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        if (!ProcessPolicy.shouldLoad(lpparam)) {
            return;
        }

        HookContext context = new HookContext(lpparam);
        HookLog.i("entry loaded: package=" + lpparam.packageName
                + ", process=" + lpparam.processName);

        HookManager manager = new HookManager(Arrays.asList(
                new HostLifecycleHook(),
                new MessageViewApi(),
                new MessageSnapshotHook(),
                new AntiRecallHook(),
                new MessageEnhancementsHook(),
                new VoiceAutoTranscribeHook(),
                new VoiceSaveHook(),
                new FavoriteVoiceForwardHook(),
                new GroupRoleHook(),
                new NotificationEnhanceHook(),
                new ImageEnhanceHook(),
                new MessageSelectionLimitHook()
        ));
        manager.installAll(context);
    }
}
