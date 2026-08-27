package dev.miniwx.wechat;

import android.widget.TextView;

import java.lang.reflect.Field;

import dev.miniwx.core.HookLog;

/** Helpers for stable field_* / holder field access in WeChat message rows. */
public final class MessageViewUi {
    private MessageViewUi() {}

    public static TextView findTextField(Object tag, String name) {
        if (tag == null) return null;
        try {
            Field field = ReflectionUtils.findField(tag.getClass(), name);
            if (field == null) return null;
            Object value = field.get(tag);
            return value instanceof TextView ? (TextView) value : null;
        } catch (Throwable t) {
            HookLog.e("failed to resolve holder field " + name, t);
            return null;
        }
    }
}
