package dev.miniwx.wechat.proto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Minimal protobuf reader for the favorite voice fields MiniWx needs. */
public final class FavoriteVoiceProto {
    public record VoiceInfo(int durationMs, String cacheType, String cacheName, String filePath) {}

    private FavoriteVoiceProto() {}

    public static VoiceInfo decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        Reader top = new Reader(data);
        while (top.hasRemaining()) {
            long tag = top.varint();
            if (tag < 0) return null;
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            if (field == 2 && wire == 2) {
                byte[] voice = top.bytes();
                return voice != null ? decodeVoice(voice) : null;
            }
            if (!top.skip(wire)) return null;
        }
        return null;
    }

    private static VoiceInfo decodeVoice(byte[] data) {
        Reader r = new Reader(data);
        int duration = 0;
        String cacheType = "";
        String cacheName = "";
        String filePath = "";
        while (r.hasRemaining()) {
            long tag = r.varint();
            if (tag < 0) break;
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            if (field == 10 && wire == 0) {
                duration = (int) r.varint();
            } else if ((field == 16 || field == 20 || field == 21) && wire == 2) {
                byte[] raw = r.bytes();
                if (raw == null) break;
                String value = new String(raw, StandardCharsets.UTF_8);
                if (field == 16) cacheType = value;
                else if (field == 20) cacheName = value;
                else filePath = value;
            } else if (!r.skip(wire)) {
                break;
            }
        }
        if (cacheName.trim().isEmpty()) return null;
        return new VoiceInfo(duration, cacheType, cacheName, filePath);
    }

    private static final class Reader {
        private final byte[] data;
        private int pos;

        Reader(byte[] data) { this.data = data; }
        boolean hasRemaining() { return pos < data.length; }

        long varint() {
            long value = 0;
            for (int shift = 0; shift < 64 && pos < data.length; shift += 7) {
                int b = data[pos++] & 0xFF;
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return value;
            }
            return -1;
        }

        byte[] bytes() {
            long lenValue = varint();
            if (lenValue < 0 || lenValue > Integer.MAX_VALUE) return null;
            int len = (int) lenValue;
            if (pos + len > data.length) return null;
            byte[] out = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return out;
        }

        boolean skip(int wire) {
            switch (wire) {
                case 0 -> { return varint() >= 0; }
                case 1 -> { if (pos + 8 > data.length) return false; pos += 8; return true; }
                case 2 -> { return bytes() != null; }
                case 5 -> { if (pos + 4 > data.length) return false; pos += 4; return true; }
                default -> { return false; }
            }
        }
    }
}
