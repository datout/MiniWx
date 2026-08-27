package dev.miniwx.hooks;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.wechat.MessageInfo;
import dev.miniwx.wechat.MessageViewApi;
import dev.miniwx.wechat.MessageViewUi;

/** Message-time, wxid-copy and first group-chat UI enhancements. */
public final class MessageEnhancementsHook implements HookItem {
    private static final String GROUP_SUFFIX_PREFIX = "  ·  ";

    @Override public String name() { return "MessageEnhancements"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        MessageViewApi.addListener((view, message, chattingItem) -> apply(view, message));
    }

    private static void apply(View root, MessageInfo message) {
        Context context = root.getContext();
        if (context == null) return;

        boolean showTime = ModuleConfigClient.getBoolean(context, FeatureFlags.MESSAGE_TIME);
        boolean copyWxid = ModuleConfigClient.getBoolean(context, FeatureFlags.COPY_WXID);
        boolean groupEnhance = ModuleConfigClient.getBoolean(context, FeatureFlags.GROUP_ENHANCE);
        if (!showTime && !copyWxid && !groupEnhance) return;

        String sender = message.senderWxId();
        TextView timeView = MessageViewUi.findTextField(root.getTag(), "timeTV");

        if (timeView != null && (showTime || copyWxid)) {
            timeView.setVisibility(View.VISIBLE);
            StringBuilder text = new StringBuilder();
            if (showTime) {
                text.append(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                        .format(new Date(message.createTime())));
            }
            if (copyWxid && sender != null) {
                if (text.length() > 0) text.append("  ·  ");
                text.append(sender);
                String copyValue = sender;
                timeView.setOnLongClickListener(v -> {
                    copyToClipboard(v.getContext(), copyValue);
                    Toast.makeText(v.getContext(), "已复制 wxid：" + copyValue, Toast.LENGTH_SHORT).show();
                    return true;
                });
                timeView.setLongClickable(true);
            } else {
                timeView.setOnLongClickListener(null);
                timeView.setLongClickable(false);
            }
            if (text.length() > 0) timeView.setText(text.toString());
            timeView.setTextSize(11f);
        }

        if (groupEnhance && message.isGroupChat() && !message.isSelfSender() && sender != null) {
            TextView userView = MessageViewUi.findTextField(root.getTag(), "userTV");
            if (userView != null) {
                String current = String.valueOf(userView.getText());
                // WeChat resets userTV on every bind. This guard only prevents duplicate suffixes
                // if a layout triggers the same bind callback more than once.
                if (!current.endsWith(GROUP_SUFFIX_PREFIX + sender)) {
                    userView.setText(current + GROUP_SUFFIX_PREFIX + sender);
                }
                userView.setTypeface(userView.getTypeface(), Typeface.NORMAL);
                if (copyWxid) {
                    String copyValue = sender;
                    userView.setOnLongClickListener(v -> {
                        copyToClipboard(v.getContext(), copyValue);
                        Toast.makeText(v.getContext(), "已复制 wxid：" + copyValue, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    userView.setLongClickable(true);
                }
            }
        }
    }

    private static void copyToClipboard(Context context, String value) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("wxid", value));
    }
}
