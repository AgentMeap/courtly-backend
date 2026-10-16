package com.se183891.badminton_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiem tra khoi dong: chan Google mock o prod va chan bat dong thoi mock + Firebase.
 */
class RequiredSecretsEnvironmentPostProcessorTest {

    private final RequiredSecretsEnvironmentPostProcessor postProcessor = new RequiredSecretsEnvironmentPostProcessor();

    @Test
    void mockGoogleIsAllowedInDev() {
        MockEnvironment env = baseEnvironment().withProperty("app.google.mock-enabled", "true");

        assertThatCode(() -> run(env)).doesNotThrowAnyException();
    }

    @Test
    void mockGoogleWithProdProfileFailsStartup() {
        MockEnvironment env = baseEnvironment()
                .withProperty("JWT_SECRET", "x".repeat(48))
                .withProperty("app.google.mock-enabled", "true");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> run(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void mockGoogleTogetherWithFirebaseFailsStartup() {
        MockEnvironment env = baseEnvironment()
                .withProperty("app.google.mock-enabled", "true")
                .withProperty("firebase.enabled", "true");

        assertThatThrownBy(() -> run(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIREBASE_ENABLED");
    }

    @Test
    void prodWithoutMockStillStarts() {
        MockEnvironment env = baseEnvironment()
                .withProperty("JWT_SECRET", "x".repeat(48))
                .withProperty("app.google.mock-enabled", "false")
                .withProperty("firebase.enabled", "true");
        env.setActiveProfiles("prod");

        assertThatCode(() -> run(env)).doesNotThrowAnyException();
    }

    private static MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("app.jwt.issuer", "courtly")
                .withProperty("spring.datasource.password", "secret");
    }

    private void run(MockEnvironment env) {
        postProcessor.postProcessEnvironment(env, new SpringApplication());
    }
}
