package com.hj.log.query.cursor;

import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 游标编解码：{@code base64(serverTsMillis + ":" + id)}。
 *
 * <p>详见 detailed-design §2.4.1。任何解码异常（base64 不合法 / 缺冒号 / 数字溢出 / 空串）
 * 一律抛 {@link BusinessException}({@link ErrorCode#QUERY_INVALID_CURSOR}, 400)。
 */
public final class CursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorCodec() {
    }

    /** {@code (serverTsMillis, id)} → URL-safe base64。 */
    public static String encode(long serverTsMillis, long id) {
        String raw = serverTsMillis + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** base64 → {@link Cursor}；非法输入 → {@code QUERY_INVALID_CURSOR}。 */
    public static Cursor decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            throw invalid();
        }
        byte[] bytes;
        try {
            bytes = DECODER.decode(base64);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        String raw = new String(bytes, StandardCharsets.UTF_8);
        int sep = raw.indexOf(':');
        if (sep <= 0 || sep == raw.length() - 1) {
            throw invalid();
        }
        try {
            long ts = Long.parseLong(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            return new Cursor(ts, id);
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.QUERY_INVALID_CURSOR, "cursor 不合法", 400);
    }

    /** 解码结果。{@code serverTsMillis} 是 {@code server_ts} 的毫秒时间戳。 */
    public static final class Cursor {
        private final long serverTsMillis;
        private final long id;

        public Cursor(long serverTsMillis, long id) {
            this.serverTsMillis = serverTsMillis;
            this.id = id;
        }

        public long getServerTsMillis() {
            return serverTsMillis;
        }

        public long getId() {
            return id;
        }
    }
}
