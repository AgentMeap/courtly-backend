package com.se183891.badminton_backend.auth.dto;

import com.se183891.badminton_backend.user.dto.UserDto;

/**
 * expiresIn: thoi gian song cua access token, tinh bang giay.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {

    public static final String BEARER = "Bearer";
}
