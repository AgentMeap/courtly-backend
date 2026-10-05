package com.se183891.badminton_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cau hinh OTP email ({@code app.otp.*}).
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        Duration ttl,
        Duration resendCooldown,
        int maxAttempts,
        int maxSendsPerHour
) {
}
