package com.se183891.badminton_backend.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceTest {

    @Test
    void generatesUrlSafeUniqueTokens() {
        String a = RefreshTokenService.generateRawToken();
        String b = RefreshTokenService.generateRawToken();

        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(b);
    }

    @Test
    void hashIsSha256Hex() {
        // SHA-256("abc")
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .hasSize(64);
    }
}
