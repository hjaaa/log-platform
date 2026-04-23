package com.hj.log.source.util;

import java.security.SecureRandom;

/**
 * API Key 明文 / 前缀 / hash 三件套。
 *
 * <p>明文格式：{@code lpk_} 前缀（4 字符）+ 36 个 Base32 字符 = 总长 40 字符。
 * 仅在 {@code POST /apps/{id}/keys} 响应里返回一次，绝不入库。
 *
 * <p>{@link #keyPrefix} = 明文前 8 位（{@code lpk_} + Base32 4 字符），用于管理员展示辨识。
 * {@link #keyHash} = SHA-256(明文) 16 进制小写，落库 {@code api_keys.key_hash}。
 */
public final class KeyMaterial {

    /** 明文 token 总长 = 前缀 4 + Base32 主体 36 = 40 字符。 */
    public static final int PLAINTEXT_LENGTH = 40;

    /** {@code key_prefix} 列长度，与 schema CHAR(8) 一致。 */
    public static final int PREFIX_LENGTH = 8;

    private static final String PREFIX = "lpk_";
    private static final int RANDOM_BYTES = 32; // 256 bit entropy
    private static final int BASE32_BODY_LENGTH = PLAINTEXT_LENGTH - PREFIX.length(); // 36

    // RFC 4648 Base32 字母表
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private static final SecureRandom RNG = new SecureRandom();

    private final String plaintext;
    private final String keyPrefix;
    private final String keyHash;

    private KeyMaterial(String plaintext, String keyPrefix, String keyHash) {
        this.plaintext = plaintext;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
    }

    public static KeyMaterial generate() {
        byte[] random = new byte[RANDOM_BYTES];
        RNG.nextBytes(random);
        String body = base32NoPad(random).substring(0, BASE32_BODY_LENGTH);
        String plain = PREFIX + body;
        return new KeyMaterial(plain, plain.substring(0, PREFIX_LENGTH), HashUtil.sha256Hex(plain));
    }

    public String getPlaintext() {
        return plaintext;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    /**
     * 简化版 Base32 编码（RFC 4648，无 {@code =} 填充）。32 字节输入 → 52 字符输出。
     * 仅用于密钥生成场景，无需考虑解码。
     */
    static String base32NoPad(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1f;
                sb.append(BASE32[idx]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1f;
            sb.append(BASE32[idx]);
        }
        return sb.toString();
    }
}
