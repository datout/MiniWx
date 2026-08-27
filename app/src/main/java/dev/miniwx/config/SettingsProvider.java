package dev.miniwx.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Tiny read-mostly bridge between the MiniWx app and hooks running inside WeChat.
 * The provider exposes feature flag reads and accepts runtime heartbeat reports.
 * It intentionally does not expose a method that lets other apps change settings.
 */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.miniwx.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    public static final String METHOD_GET_BOOL = "get_bool";
    public static final String METHOD_REPORT_RUNTIME = "report_runtime";
    public static final String METHOD_GET_RUNTIME = "get_runtime";

    private static final String RUNTIME_PREFS = "runtime";
    private static final String KEY_LAST_SEEN = "last_seen";
    private static final String KEY_WECHAT_VERSION = "wechat_version";
    private static final String KEY_PROCESS = "process";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext() == null) {
            return Bundle.EMPTY;
        }

        if (METHOD_GET_BOOL.equals(method)) {
            Bundle out = new Bundle();
            out.putBoolean("value", FeatureFlags.getLocal(getContext(), arg));
            return out;
        }

        if (METHOD_REPORT_RUNTIME.equals(method)) {
            String version = extras != null ? extras.getString(KEY_WECHAT_VERSION, "unknown") : "unknown";
            String process = extras != null ? extras.getString(KEY_PROCESS, "unknown") : "unknown";
            getContext().getSharedPreferences(RUNTIME_PREFS, 0)
                    .edit()
                    .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
                    .putString(KEY_WECHAT_VERSION, version)
                    .putString(KEY_PROCESS, process)
                    .apply();
            return Bundle.EMPTY;
        }

        if (METHOD_GET_RUNTIME.equals(method)) {
            var prefs = getContext().getSharedPreferences(RUNTIME_PREFS, 0);
            Bundle out = new Bundle();
            out.putLong(KEY_LAST_SEEN, prefs.getLong(KEY_LAST_SEEN, 0L));
            out.putString(KEY_WECHAT_VERSION, prefs.getString(KEY_WECHAT_VERSION, "未检测"));
            out.putString(KEY_PROCESS, prefs.getString(KEY_PROCESS, "未检测"));
            return out;
        }

        return super.call(method, arg, extras);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
