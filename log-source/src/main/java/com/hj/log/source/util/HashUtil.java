package com.hj.log.source.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 工具：明文 → 16 进制小写串，与 {@code api_keys.key_hash} 列存储格式一致。 */
public final class HashUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HashUtil() {
    }

    public static String sha256Hex(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                out[i * 2] = HEX_CHARS[v >>> 4];
                out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，不可能找不到；防御性 wrap
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
