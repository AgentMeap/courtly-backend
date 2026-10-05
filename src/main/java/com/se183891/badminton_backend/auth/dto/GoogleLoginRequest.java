package com.se183891.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "idToken không được để trống")
        String idToken
) {
}
