package dev.miniwx.wechat;

/** Lightweight wrapper around WeChat's message model using stable field_* database fields. */
public final class MessageInfo {
    private final Object instance;

    public MessageInfo(Object instance) {
        this.instance = instance;
    }

    public Object instance() { return instance; }

    public long id() { return longValue("field_msgId"); }
    public long serverId() { return longValue("field_msgSvrId"); }
    public long createTime() { return longValue("field_createTime"); }
    public int typeCode() { return intValue("field_type"); }
    public int isSend() { return intValue("field_isSend"); }
    public String talker() { return stringValue("field_talker"); }
    public String content() { return stringValue("field_content"); }

    public boolean isSelfSender() {
        return isSend() != 0;
    }

    public boolean isGroupChat() {
        return talker().endsWith("@chatroom");
    }

    /**
     * Returns the sender wxid when it can be derived without guessing.
     * Outgoing messages intentionally return null until MiniWx resolves the logged-in wxid.
     */
    public String senderWxId() {
        if (isSelfSender()) return null;
        if (!isGroupChat()) return emptyToNull(talker());

        String text = content();
        int split = text.indexOf(":\n");
        if (split < 0) split = text.indexOf(':');
        if (split <= 0) return null;
        String sender = text.substring(0, split).trim();
        return emptyToNull(sender);
    }

    private int intValue(String name) {
        Object value = ReflectionUtils.getField(instance, name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long longValue(String name) {
        Object value = ReflectionUtils.getField(instance, name);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String stringValue(String name) {
        Object value = ReflectionUtils.getField(instance, name);
        return value != null ? String.valueOf(value) : "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
