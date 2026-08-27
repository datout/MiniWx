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
        TextView subtitle = text("0.5.0 · LSPosed 主框架 / Native + Zygisk 可选扩展", 14, false);
        subtitle.setAlpha(0.65f);
        root.addView(subtitle);

        addGap(root, 18);
        addSectionTitle(root, "运行状态");
        addStatusCard(root);

        addGap(root, 18);
        addSectionTitle(root, "防撤回");
        addSwitch(root, "防撤回", "保留对方撤回的原消息（实验性）", FeatureFlags.ANTI_RECALL, true);
        addSwitch(root, "撤回消息提示", "拦截后显示微信原始的撤回提示文字", FeatureFlags.RECALL_NOTICE, true);
        addSwitch(root, "自己撤回正常", "识别为‘你撤回…’时不拦截；当前为文本兼容逻辑", FeatureFlags.OWN_RECALL_NORMAL, true);

        addGap(root, 18);
        addSectionTitle(root, "聊天与消息");
        addSwitch(root, "复制 wxid", "消息时间行显示发送者 wxid；长按即可复制（收到的消息）", FeatureFlags.COPY_WXID, true);
        addSwitch(root, "消息详细时间", "显示 yyyy/MM/dd HH:mm:ss 精确时间", FeatureFlags.MESSAGE_TIME, true);

        addGap(root, 18);
        addSectionTitle(root, "媒体增强");
        addSwitch(root, "语音相关增强", "功能入口已纳入；将拆分为播放/转文字/保存等子功能", FeatureFlags.VOICE_ENHANCE, false);
        addSwitch(root, "图片增强", "第一阶段：打开图片/视频时自动点击‘查看原图/原视频’", FeatureFlags.IMAGE_ENHANCE, true);

        addGap(root, 18);
        addSectionTitle(root, "系统与群聊");
        addSwitch(root, "通知增强", "功能入口已纳入；具体通知 Hook 待适配", FeatureFlags.NOTIFICATION_ENHANCE, false);
        addSwitch(root, "群聊增强", "第一阶段：群消息昵称旁显示发送者 wxid", FeatureFlags.GROUP_ENHANCE, true);

        addGap(root, 22);
        TextView note = text(
                "说明：0.5.0 已接入防撤回、精确时间、wxid 复制、自动原图和群聊发送者 ID。语音与通知增强仍保持禁用，下一批再接真实 Hook。所有功能均为本地增强，不修改微信网络协议。",
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
