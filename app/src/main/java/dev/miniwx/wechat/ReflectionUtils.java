package dev.miniwx.wechat;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Small reflection helpers used by version-tolerant WeChat UI hooks. */
public final class ReflectionUtils {
    private ReflectionUtils() {}

    public static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public static Object getField(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = findField(target.getClass(), name);
            return field != null ? field.get(target) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static <T> T firstFieldValue(Object target, Class<T> wantedType) {
        if (target == null) return null;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!wantedType.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (wantedType.isInstance(value)) return wantedType.cast(value);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static <T> List<T> allFieldValues(Object target, Class<T> wantedType) {
        List<T> values = new ArrayList<>();
        if (target == null) return values;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!wantedType.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (wantedType.isInstance(value)) values.add(wantedType.cast(value));
                } catch (Throwable ignored) {
                }
            }
        }
        return values;
    }

    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static boolean hasField(Object target, String name) {
        return target != null && findField(target.getClass(), name) != null;
    }

    public static boolean looksLikeMessageObject(Object value) {
        return value != null
                && hasField(value, "field_msgId")
                && hasField(value, "field_createTime")
                && hasField(value, "field_isSend")
                && hasField(value, "field_talker");
    }

    public static View firstViewField(Object target) {
        return firstFieldValue(target, View.class);
    }
}
