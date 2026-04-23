package com.hj.log.source.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashUtilTest {

    @Test
    void should_match_known_sha256() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(HashUtil.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void should_be_deterministic() {
        String h1 = HashUtil.sha256Hex("lpk_AB12CD34EF");
        String h2 = HashUtil.sha256Hex("lpk_AB12CD34EF");
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void should_return_lowercase_hex() {
        assertThat(HashUtil.sha256Hex("test")).matches("[0-9a-f]{64}");
    }

    @Test
    void should_return_null_for_null_input() {
        assertThat(HashUtil.sha256Hex(null)).isNull();
    }
}
