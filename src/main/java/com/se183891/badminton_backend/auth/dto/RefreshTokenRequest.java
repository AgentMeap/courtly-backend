package com.se183891.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dung cho ca /api/auth/refresh va /api/auth/logout.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken không được để trống")
        @Size(max = 200, message = "refreshToken không hợp lệ")
        String refreshToken
) {
}
