package dev.miniwx.wechat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small in-process cache of original message metadata used by recall and other hooks. */
public final class MessageSnapshotCache {
    private static final int MAX = 2048;
    private static final Map<Long, Snapshot> CACHE = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Snapshot> eldest) {
            return size() > MAX;
        }
    };

    private MessageSnapshotCache() {}

    public static void put(MessageInfo info) {
        if (info == null || info.serverId() == 0L) return;
        synchronized (CACHE) {
            CACHE.put(info.serverId(), new Snapshot(
                    info.serverId(), info.isSelfSender(), info.talker(), info.createTime(), info.typeCode()
            ));
        }
    }

    public static Snapshot get(long serverId) {
        if (serverId == 0L) return null;
        synchronized (CACHE) {
            return CACHE.get(serverId);
        }
    }

    public record Snapshot(long serverId, boolean selfSender, String talker, long createTime, int typeCode) {}
}
