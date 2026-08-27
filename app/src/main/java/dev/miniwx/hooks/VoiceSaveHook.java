package dev.miniwx.hooks;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.core.HookResolveExecutor;
import dev.miniwx.wechat.MessageInfo;
import dev.miniwx.wechat.MessageViewApi;
import dev.miniwx.wechat.MessageViewUi;
import dev.miniwx.wechat.WeChatVoiceApi;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Adds a lightweight save action for voice messages and exports the original WeChat voice file. */
public final class VoiceSaveHook implements HookItem {
    private static final String SAVE_SUFFIX = "  ·  保存语音";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Override public String name() { return "VoiceSave"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        MessageViewApi.addListener(this::onBind);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!STARTED.compareAndSet(false, true)) return;
                Context host = (Context) param.args[0];
                HookResolveExecutor.submit("VoiceSave", () -> WeChatVoiceApi.ensureResolved(host, context));
            }
        });
    }

    private void onBind(View root, MessageInfo message, Object chattingItem) {
        TextView time = MessageViewUi.findTextField(root.getTag(), "timeTV");
        if (time == null) return;
        Context context = root.getContext();
        boolean enabled = context != null
                && ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_ENHANCE)
                && ModuleConfigClient.getBoolean(context, FeatureFlags.VOICE_SAVE_ORIGINAL)
                && message.isVoice();

        String current = String.valueOf(time.getText());
        if (!enabled) {
            time.setOnClickListener(null);
            if (current.endsWith(SAVE_SUFFIX)) {
                time.setText(current.substring(0, current.length() - SAVE_SUFFIX.length()));
            } else if ("保存语音".equals(current)) {
                time.setText("");
            }
            return;
        }

        time.setVisibility(View.VISIBLE);
        if (current.trim().isEmpty()) time.setText("保存语音");
        else if (!current.endsWith(SAVE_SUFFIX)) time.setText(current + SAVE_SUFFIX);
        String encPath = message.imagePath();
        long serverId = message.serverId();
        time.setOnClickListener(v -> saveAsync(v.getContext(), encPath, serverId));
    }

    private static void saveAsync(Context context, String encPath, long serverId) {
        Toast.makeText(context, "正在保存原始语音…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (encPath == null || encPath.trim().isEmpty()) throw new IllegalStateException("field_imgPath 为空");
                String source = WeChatVoiceApi.getVoiceFullPath(encPath);
                if (source == null || source.trim().isEmpty()) throw new IllegalStateException("无法解析语音完整路径");
                File src = new File(source);
                if (!src.isFile()) throw new IllegalStateException("语音文件不存在: " + source);
                String name = exportName(src, serverId);
                String location = export(context, src, name);
                show(context, "已保存原始语音：" + location);
            } catch (Throwable t) {
                HookLog.e("voice save failed", t);
                show(context, "语音保存失败，请查看 LSPosed 日志");
            }
        }, "MiniWx-VoiceSave").start();
    }

    private static String export(Context context, File source, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniWx");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try (InputStream in = new FileInputStream(source); OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("MediaStore output stream unavailable");
                copy(in, out);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            return "Download/MiniWx/" + name;
        }

        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) throw new IllegalStateException("external files unavailable");
        File dir = new File(root, "MiniWx");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        File outFile = new File(dir, name);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(outFile)) {
            copy(in, out);
        }
        return outFile.getAbsolutePath();
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) out.write(buffer, 0, read);
        }
    }

    private static String exportName(File source, long serverId) {
        String original = source.getName();
        String ext = ".amr";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) ext = original.substring(dot);
        return "MiniWx_voice_" + serverId + "_" + System.currentTimeMillis() + ext;
    }

    private static void show(Context context, String text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
    }
}
