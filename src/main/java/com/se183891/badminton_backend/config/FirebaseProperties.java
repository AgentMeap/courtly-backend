package com.se183891.badminton_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh Firebase ({@code firebase.*}). Mac dinh enabled=false.
 */
@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        boolean enabled,
        String projectId,
        String serviceAccountPath
) {
}
