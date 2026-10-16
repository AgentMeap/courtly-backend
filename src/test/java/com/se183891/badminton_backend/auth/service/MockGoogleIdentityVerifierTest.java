package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.service.GoogleIdentityVerifier.GoogleIdentity;
import com.se183891.badminton_backend.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockGoogleIdentityVerifierTest {

    private final MockGoogleIdentityVerifier verifier = new MockGoogleIdentityVerifier(new MockEnvironment());

    @Test
    void parsesEmailAndNameWhichMayContainColons() {
        GoogleIdentity identity = verifier.verify("mock: Demo@Example.com :Nguyễn: Văn A");

        assertThat(identity.uid()).isEqualTo("mock:demo@example.com");
        assertThat(identity.email()).isEqualTo("demo@example.com");
        assertThat(identity.name()).isEqualTo("Nguyễn: Văn A");
        assertThat(identity.picture()).isNull();
        assertThat(verifier.isMock()).isTrue();
    }

    @Test
    void nameIsOptional() {
        assertThat(verifier.verify("mock:demo@example.com").name()).isNull();
        assertThat(verifier.verify("mock:demo@example.com:  ").name()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "mock", "mock:", "mock::Name", "mock:not-an-email:Name",
            "Mock:demo@example.com:Name", "google:demo@example.com:Name", "mock:a b@example.com:Name"})
    void malformedTokenIsUnauthorized(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void emailTooLongForFirebaseUidColumnIsUnauthorized() {
        String email = "a".repeat(120) + "@example.com";
        assertThatThrownBy(() -> verifier.verify("mock:" + email + ":X")).isInstanceOf(ApiException.class);
    }

    @Test
    void refusesToStartWithProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new MockGoogleIdentityVerifier(prod)).isInstanceOf(IllegalStateException.class);
    }
}
