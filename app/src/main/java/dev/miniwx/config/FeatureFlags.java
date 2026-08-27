package dev.miniwx.config;

import android.content.Context;
import android.content.SharedPreferences;

/** Central list of MiniWx user-facing feature flags. */
public final class FeatureFlags {
    public static final String PREFS = "features";

    public static final String ANTI_RECALL = "anti_recall";
    public static final String RECALL_NOTICE = "recall_notice";
    public static final String OWN_RECALL_NORMAL = "own_recall_normal";
    public static final String COPY_WXID = "copy_wxid";
    public static final String MESSAGE_TIME = "message_time";
    public static final String VOICE_ENHANCE = "voice_enhance";
    public static final String VOICE_AUTO_TRANSCRIBE = "voice_auto_transcribe";
    public static final String IMAGE_ENHANCE = "image_enhance";
    public static final String NOTIFICATION_ENHANCE = "notification_enhance";
    public static final String NOTIFICATION_MESSAGING_STYLE = "notification_messaging_style";
    public static final String NOTIFICATION_MERGE = "notification_merge";
    public static final String GROUP_ENHANCE = "group_enhance";
    public static final String GROUP_ROLE_BADGE = "group_role_badge";
    public static final String GROUP_SHOW_MEMBER = "group_show_member";

    private FeatureFlags() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean defaultValue(String key) {
        return switch (key) {
            case ANTI_RECALL, RECALL_NOTICE, OWN_RECALL_NORMAL,
                    VOICE_AUTO_TRANSCRIBE, NOTIFICATION_MESSAGING_STYLE, NOTIFICATION_MERGE,
                    GROUP_ROLE_BADGE -> true;
            default -> false;
        };
    }

    public static boolean getLocal(Context context, String key) {
        return prefs(context).getBoolean(key, defaultValue(key));
    }

    public static void setLocal(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }
}
