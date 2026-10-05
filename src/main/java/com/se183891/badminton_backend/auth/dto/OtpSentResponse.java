package com.se183891.badminton_backend.auth.dto;

/**
 * expiresIn: thoi gian song cua ma (giay); resendAfter: phai cho bao nhieu giay moi duoc gui lai.
 */
public record OtpSentResponse(
        String message,
        long expiresIn,
        long resendAfter
) {
}
