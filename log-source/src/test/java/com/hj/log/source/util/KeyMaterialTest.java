package com.hj.log.source.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyMaterialTest {

    @Test
    void should_have_total_length_40() {
        KeyMaterial m = KeyMaterial.generate();
        assertThat(m.getPlaintext()).hasSize(KeyMaterial.PLAINTEXT_LENGTH);
        assertThat(m.getKeyPrefix()).hasSize(KeyMaterial.PREFIX_LENGTH);
    }

    @Test
    void should_start_with_lpk_prefix() {
        KeyMaterial m = KeyMaterial.generate();
        assertThat(m.getPlaintext()).startsWith("lpk_");
        assertThat(m.getKeyPrefix()).startsWith("lpk_");
    }

    @Test
    void key_prefix_should_be_first_8_chars_of_plaintext() {
        KeyMaterial m = KeyMaterial.generate();
        assertThat(m.getKeyPrefix()).isEqualTo(m.getPlaintext().substring(0, 8));
    }

    @Test
    void key_hash_should_match_sha256_of_plaintext() {
        KeyMaterial m = KeyMaterial.generate();
        assertThat(m.getKeyHash()).isEqualTo(HashUtil.sha256Hex(m.getPlaintext())).hasSize(64);
    }

    @Test
    void each_call_should_produce_different_plaintext() {
        KeyMaterial a = KeyMaterial.generate();
        KeyMaterial b = KeyMaterial.generate();
        assertThat(a.getPlaintext()).isNotEqualTo(b.getPlaintext());
    }

    @Test
    void base32_body_should_only_contain_rfc4648_chars() {
        // 主体 36 字符（去掉 lpk_ 前缀）应满足 [A-Z2-7]+
        KeyMaterial m = KeyMaterial.generate();
        String body = m.getPlaintext().substring(4);
        assertThat(body).matches("[A-Z2-7]{36}");
    }
}
