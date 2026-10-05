package com.se183891.badminton_backend.security;

import com.se183891.badminton_backend.config.JwtProperties;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private static JwtService service(Duration ttl) {
        return new JwtService(new JwtProperties(SECRET, "courtly", ttl, Duration.ofDays(7)));
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("a@b.vn");
        user.setRole(Role.ADMIN);
        return user;
    }

    @Test
    void roundTripsClaims() {
        JwtService jwt = service(Duration.ofMinutes(30));
        User user = user();

        AuthUser parsed = jwt.parseAccessToken(jwt.generateAccessToken(user));

        assertThat(parsed.id()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo("a@b.vn");
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(1800);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = service(Duration.ofSeconds(-5));
        String token = jwt.generateAccessToken(user());

        assertThatThrownBy(() -> jwt.parseAccessToken(token))
                .isInstanceOfSatisfying(JwtService.TokenValidationException.class,
                        ex -> assertThat(ex.isExpired()).isTrue());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtService(new JwtProperties("another-secret-another-secret-0123456789ab",
                "courtly", Duration.ofMinutes(5), Duration.ofDays(7))).generateAccessToken(user());

        assertThatThrownBy(() -> service(Duration.ofMinutes(5)).parseAccessToken(foreign))
                .isInstanceOfSatisfying(JwtService.TokenValidationException.class,
                        ex -> assertThat(ex.isExpired()).isFalse());
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> service(Duration.ofMinutes(5)).parseAccessToken("not-a-jwt"))
                .isInstanceOf(JwtService.TokenValidationException.class);
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties("too-short", "courtly", Duration.ofMinutes(5), Duration.ofDays(7))))
                .isInstanceOf(IllegalStateException.class);
    }
}
