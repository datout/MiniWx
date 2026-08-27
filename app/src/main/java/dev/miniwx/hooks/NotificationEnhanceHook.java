package dev.miniwx.hooks;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Person;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.miniwx.config.FeatureFlags;
import dev.miniwx.config.ModuleConfigClient;
import dev.miniwx.core.HookContext;
import dev.miniwx.core.HookItem;
import dev.miniwx.core.HookLog;
import dev.miniwx.hooks.HostLifecycleHook;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Local notification evolution: MessagingStyle history, stable per-conversation IDs and
 * cancel-ID translation. It operates on notifications WeChat is already about to post.
 */
public final class NotificationEnhanceHook implements HookItem {
    private static final int MAX_HISTORY = 8;
    private static final Map<String, Deque<Entry>> HISTORY = new ConcurrentHashMap<>();
    private static final Map<String, MergedRef> ORIGINAL_TO_MERGED = new ConcurrentHashMap<>();
    private static final Map<String, String> ORIGINAL_TO_CONVERSATION = new ConcurrentHashMap<>();

    @Override public String name() { return "NotificationEnhance"; }
    @Override public boolean enabled() { return true; }

    @Override
    public void install(HookContext context) {
        XposedHelpers.findAndHookMethod(
                NotificationManager.class,
                "notify",
                int.class,
                Notification.class,
                new NotifyHook(false)
        );
        XposedHelpers.findAndHookMethod(
                NotificationManager.class,
                "notify",
                String.class,
                int.class,
                Notification.class,
                new NotifyHook(true)
        );
        XposedHelpers.findAndHookMethod(
                NotificationManager.class,
                "cancel",
                int.class,
                new CancelHook(false)
        );
        XposedHelpers.findAndHookMethod(
                NotificationManager.class,
                "cancel",
                String.class,
                int.class,
                new CancelHook(true)
        );
        XposedHelpers.findAndHookMethod(
                NotificationManager.class,
                "cancelAll",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        HISTORY.clear();
                        ORIGINAL_TO_MERGED.clear();
                        ORIGINAL_TO_CONVERSATION.clear();
                    }
                }
        );
    }

    private static final class NotifyHook extends XC_MethodHook {
        private final boolean tagged;

        NotifyHook(boolean tagged) {
            this.tagged = tagged;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            Context context = HostLifecycleHook.getHostContext();
            if (context == null || !ModuleConfigClient.getBoolean(context, FeatureFlags.NOTIFICATION_ENHANCE)) return;

            int idIndex = tagged ? 1 : 0;
            int notificationIndex = tagged ? 2 : 1;
            String tag = tagged ? (String) param.args[0] : null;
            int originalId = (Integer) param.args[idIndex];
            Notification notification = (Notification) param.args[notificationIndex];
            if (notification == null || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

            Parsed parsed = parse(notification);
            if (parsed == null) return;

            String originalKey = notificationKey(tag, originalId);
            boolean merge = ModuleConfigClient.getBoolean(context, FeatureFlags.NOTIFICATION_MERGE);
            if (merge) {
                int mergedId = stableConversationId(parsed.conversation);
                String mergedTag = tagged ? "MiniWx:" + Integer.toHexString(mergedId) : null;
                ORIGINAL_TO_MERGED.put(originalKey, new MergedRef(mergedTag, mergedId));
                ORIGINAL_TO_CONVERSATION.put(originalKey, parsed.conversation);
                if (tagged) param.args[0] = mergedTag;
                param.args[idIndex] = mergedId;
            }

            if (!ModuleConfigClient.getBoolean(context, FeatureFlags.NOTIFICATION_MESSAGING_STYLE)) return;
            try {
                appendHistory(parsed);
                Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification);
                Person self = new Person.Builder().setName("我").build();
                Notification.MessagingStyle style = new Notification.MessagingStyle(self);
                if (parsed.group) {
                    style.setConversationTitle(parsed.conversation);
                    style.setGroupConversation(true);
                } else {
                    style.setGroupConversation(false);
                }

                Icon notificationIcon = ModuleConfigClient.getBoolean(context, FeatureFlags.NOTIFICATION_AVATAR)
                        ? notification.getLargeIcon() : null;
                Deque<Entry> entries = HISTORY.get(parsed.conversation);
                if (entries != null) {
                    synchronized (entries) {
                        for (Entry entry : entries) {
                            Person.Builder senderBuilder = new Person.Builder().setName(entry.sender);
                            if (notificationIcon != null) senderBuilder.setIcon(notificationIcon);
                            style.addMessage(entry.text, entry.when, senderBuilder.build());
                        }
                    }
                }
                builder.setStyle(style);
                param.args[notificationIndex] = builder.build();
            } catch (Throwable t) {
                HookLog.e("NotificationEnhance rebuild failed", t);
            }
        }
    }

    private static final class CancelHook extends XC_MethodHook {
        private final boolean tagged;

        CancelHook(boolean tagged) {
            this.tagged = tagged;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            Context context = HostLifecycleHook.getHostContext();
            if (context == null || !ModuleConfigClient.getBoolean(context, FeatureFlags.NOTIFICATION_ENHANCE)) return;
            int idIndex = tagged ? 1 : 0;
            String tag = tagged ? (String) param.args[0] : null;
            int originalId = (Integer) param.args[idIndex];
            String key = notificationKey(tag, originalId);
            MergedRef merged = ORIGINAL_TO_MERGED.remove(key);
            String conversation = ORIGINAL_TO_CONVERSATION.remove(key);
            if (merged != null) {
                if (tagged) param.args[0] = merged.tag;
                param.args[idIndex] = merged.id;
            }
            if (conversation != null) HISTORY.remove(conversation);
        }
    }

    private static Parsed parse(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return null;
        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(titleCs) || TextUtils.isEmpty(textCs)) return null;

        String title = titleCs.toString().trim();
        String text = textCs.toString().trim();
        if (title.isEmpty() || text.isEmpty()) return null;

        boolean group = false;
        String sender = title;
        String body = text;

        CharSequence conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (!TextUtils.isEmpty(conversationTitle)) {
            group = true;
            title = conversationTitle.toString().trim();
        }

        int colon = firstColon(text);
        if (colon > 0 && colon <= 32) {
            String prefix = text.substring(0, colon).trim();
            String suffix = text.substring(colon + 1).trim();
            if (!prefix.isEmpty() && !suffix.isEmpty()) {
                // WeChat group notifications commonly use “昵称:消息”. This is also a useful
                // fallback when the original notification is not yet MessagingStyle.
                group = true;
                sender = stripCountPrefix(prefix);
                body = suffix;
            }
        }

        long when = notification.when > 0 ? notification.when : System.currentTimeMillis();
        return new Parsed(title, sender, body, when, group);
    }

    private static int firstColon(String value) {
        int ascii = value.indexOf(':');
        int full = value.indexOf('：');
        if (ascii < 0) return full;
        if (full < 0) return ascii;
        return Math.min(ascii, full);
    }

    private static String stripCountPrefix(String value) {
        String text = value;
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end > 0 && end + 1 < text.length()) text = text.substring(end + 1).trim();
        }
        return text;
    }

    private static void appendHistory(Parsed parsed) {
        Deque<Entry> entries = HISTORY.computeIfAbsent(parsed.conversation, k -> new ArrayDeque<>());
        synchronized (entries) {
            Entry last = entries.peekLast();
            if (last != null && last.sender.equals(parsed.sender)
                    && last.text.equals(parsed.text) && Math.abs(last.when - parsed.when) < 1000L) return;
            entries.addLast(new Entry(parsed.sender, parsed.text, parsed.when));
            while (entries.size() > MAX_HISTORY) entries.removeFirst();
        }
    }

    private static int stableConversationId(String conversation) {
        return 0x4D000000 | (conversation.hashCode() & 0x00FFFFFF);
    }

    private static String notificationKey(String tag, int id) {
        return (tag == null ? "" : tag) + '\u0000' + id;
    }

    private static final class Parsed {
        final String conversation;
        final String sender;
        final String text;
        final long when;
        final boolean group;

        Parsed(String conversation, String sender, String text, long when, boolean group) {
            this.conversation = conversation;
            this.sender = sender;
            this.text = text;
            this.when = when;
            this.group = group;
        }
    }

    private static final class Entry {
        final String sender;
        final String text;
        final long when;

        Entry(String sender, String text, long when) {
            this.sender = sender;
            this.text = text;
            this.when = when;
        }
    }
    private static final class MergedRef {
        final String tag;
        final int id;

        MergedRef(String tag, int id) {
            this.tag = tag;
            this.id = id;
        }
    }

}
