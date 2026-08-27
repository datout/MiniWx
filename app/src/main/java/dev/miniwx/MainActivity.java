package dev.miniwx;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.SettingsProvider;

public final class MainActivity extends Activity {
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("MiniWx");
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("MiniWx", 30, true);
        root.addView(title);
        TextView subtitle = text(moduleVersion() + " · LSPosed 主框架 / Native + Zygisk 可选扩展", 14, false);
        subtitle.setAlpha(0.65f);
        root.addView(subtitle);

        addGap(root, 18);
        addSectionTitle(root, "运行状态");
        addStatusCard(root);
        addGap(root, 12);
        addHookStatusCard(root);

        addGap(root, 18);
        addSectionTitle(root, "防撤回");
        addSwitch(root, "防撤回", "保留对方撤回的原消息（实验性）", FeatureFlags.ANTI_RECALL, true);
        addSwitch(root, "撤回消息提示", "拦截后显示微信原始的撤回提示文字", FeatureFlags.RECALL_NOTICE, true);
        addSwitch(root, "自己撤回正常", "优先查询微信本地 message 数据库的 isSend；数据库未就绪/未命中时才回退缓存和提示文本", FeatureFlags.OWN_RECALL_NORMAL, true);

        addGap(root, 18);
        addSectionTitle(root, "聊天与消息");
        addSwitch(root, "复制 wxid", "消息时间行显示发送者 wxid；长按即可复制（收到的消息）", FeatureFlags.COPY_WXID, true);
        addSwitch(root, "消息详细时间", "显示 yyyy/MM/dd HH:mm:ss 精确时间", FeatureFlags.MESSAGE_TIME, true);

        addGap(root, 18);
        addSectionTitle(root, "媒体增强");
        addSwitch(root, "语音相关增强", "启用语音增强总开关", FeatureFlags.VOICE_ENHANCE, true);
        addSwitch(root, "自动语音转文字", "收到语音后调用微信自身 TransformComponent 自动发起转文字", FeatureFlags.VOICE_AUTO_TRANSCRIBE, true);
        addSwitch(root, "收藏语音转发", "允许从‘我 → 收藏’选择单条语音并转发给好友/群聊", FeatureFlags.VOICE_FAVORITE_FORWARD, true);
        addSwitch(root, "保存原始语音", "语音消息时间行出现‘保存语音’，点击保存到 Download/MiniWx（原始编码，不转 MP3）", FeatureFlags.VOICE_SAVE_ORIGINAL, true);
        addSwitch(root, "图片增强", "打开图片/视频时自动点击‘查看原图/原视频’", FeatureFlags.IMAGE_ENHANCE, true);

        addGap(root, 18);
        addSectionTitle(root, "通知增强");
        addSwitch(root, "通知增强", "启用 MiniWx 通知处理总开关", FeatureFlags.NOTIFICATION_ENHANCE, true);
        addSwitch(root, "MessagingStyle", "把微信消息通知整理成 Android 对话式通知", FeatureFlags.NOTIFICATION_MESSAGING_STYLE, true);
        addSwitch(root, "同会话合并", "同一联系人/群聊复用稳定通知 ID，并兼容微信 cancel", FeatureFlags.NOTIFICATION_MERGE, true);
        addSwitch(root, "通知头像", "MessagingStyle 消息沿用微信原通知的大图标/头像（群成员独立头像后续继续完善）", FeatureFlags.NOTIFICATION_AVATAR, true);

        addGap(root, 18);
        addSectionTitle(root, "群聊增强");
        addSwitch(root, "群聊增强", "群消息昵称旁显示发送者 wxid，并提供身份标签能力", FeatureFlags.GROUP_ENHANCE, true);
        addSwitch(root, "群主/管理员标签", "读取微信群资料显示群主、管理员身份", FeatureFlags.GROUP_ROLE_BADGE, true);
        addSwitch(root, "显示普通成员标签", "同时给普通群成员显示‘成员’标签；默认关闭以减少界面占用", FeatureFlags.GROUP_SHOW_MEMBER, true);

        addGap(root, 18);
        addSectionTitle(root, "操作增强");
        addSwitch(root, "解除消息多选 100 条限制", "兼容旧版 ChattingDataAdapterV3；若当前微信已更换实现，Hook 状态会显示失败而不会强行注入", FeatureFlags.MESSAGE_SELECTION_UNLIMITED, true);

        addGap(root, 22);
        TextView note = text(
                "说明：0.7.1 防撤回已接入微信本地 message 数据库：根据撤回事件的 msgSvrId 直接读取原消息 isSend，微信重启后仍可判断自己/对方消息；数据库不可用时才退回进程缓存和提示文本。其余 0.7 功能保持不变。",
                13,
                false
        );
        note.setAlpha(0.65f);
        root.addView(note);

        setContentView(scroll);
    }

    private void addStatusCard(LinearLayout root) {
        Bundle runtime = null;
        try {
            runtime = getContentResolver().call(
                    SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_RUNTIME,
                    null,
                    null
            );
        } catch (Throwable ignored) {
        }

        long lastSeen = runtime != null ? runtime.getLong("last_seen", 0L) : 0L;
        String wechatVersion = runtime != null ? runtime.getString("wechat_version", "未检测") : "未检测";
        String process = runtime != null ? runtime.getString("process", "未检测") : "未检测";
        boolean recent = lastSeen > 0 && System.currentTimeMillis() - lastSeen < 10 * 60 * 1000L;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF5F5F5);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x22000000);
        card.setBackground(background);

        card.addView(text("模块状态：" + (recent ? "已检测到微信注入" : "尚未检测到近期注入"), 16, true));
        card.addView(text("微信版本：" + wechatVersion, 14, false));
        card.addView(text("微信进程：" + process, 14, false));
        card.addView(text("最后心跳：" + (lastSeen > 0 ? DateFormat.getDateTimeInstance().format(new Date(lastSeen)) : "无"), 14, false));
        card.addView(text("本机微信安装：" + installedWeChatVersion(), 14, false));

        TextView hint = text("启用 LSPosed 后请强制停止并重新打开微信，再回到此页刷新。", 12, false);
        hint.setAlpha(0.65f);
        hint.setPadding(0, dp(8), 0, 0);
        card.addView(hint);

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addHookStatusCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF8F8F8);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x22000000);
        card.setBackground(background);

        card.addView(text("Hook 兼容状态", 16, true));
        String[] hooks = {
                "MessageViewApi", "WeChatDatabase", "MessageSnapshot", "AntiRecall",
                "VoiceAutoTranscribe", "VoiceSave", "FavoriteVoiceForward",
                "GroupRole", "ImageEnhance", "MessageSelectionLimit"
        };
        for (String hook : hooks) {
            Bundle status = null;
            try {
                status = getContentResolver().call(
                        SettingsProvider.URI, SettingsProvider.METHOD_GET_HOOK, hook, null);
            } catch (Throwable ignored) {
            }
            String value = status != null ? status.getString("status", "未检测") : "未检测";
            String detail = status != null ? status.getString("detail", "") : "";
            TextView line = text(hook + "：" + value, 13, false);
            card.addView(line);
            if (detail != null && !detail.trim().isEmpty() && !"正常".equals(value)) {
                TextView d = text("  " + detail, 11, false);
                d.setAlpha(0.58f);
                card.addView(d);
            }
        }
        TextView hint = text("状态在微信进程完成 DexKit 定位后更新；微信升级后可先看这里判断是哪一项失效。", 12, false);
        hint.setAlpha(0.65f);
        hint.setPadding(0, dp(8), 0, 0);
        card.addView(hint);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String moduleVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "unknown";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private String installedWeChatVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo("com.tencent.mm", 0);
            return info.versionName != null ? info.versionName : "未知";
        } catch (Throwable t) {
            return "未安装/不可见";
        }
    }

    private void addSwitch(LinearLayout root, String title, String description, String key, boolean implemented) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView label = text(title + (implemented ? "" : "  · 待适配"), 16, false);
        labels.addView(label);
        TextView desc = text(description, 12, false);
        desc.setAlpha(0.62f);
        labels.addView(desc);

        Switch toggle = new Switch(this);
        toggle.setChecked(FeatureFlags.getLocal(this, key));
        toggle.setEnabled(implemented);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.setLocal(this, key, isChecked);
            Toast.makeText(this, title + (isChecked ? " 已开启" : " 已关闭"), Toast.LENGTH_SHORT).show();
        });

        row.addView(labels, labelsLp);
        row.addView(toggle);
        root.addView(row);
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addSectionTitle(LinearLayout root, String value) {
        TextView view = text(value, 18, true);
        view.setPadding(0, 0, 0, dp(4));
        root.addView(view);
    }

    private void addGap(LinearLayout root, int heightDp) {
        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }
}
