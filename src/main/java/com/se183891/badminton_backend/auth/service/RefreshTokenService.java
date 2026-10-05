package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.entity.RefreshToken;
import com.se183891.badminton_backend.auth.repository.RefreshTokenRepository;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.config.JwtProperties;
import com.se183891.badminton_backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token: chuoi ngau nhien 256-bit (SecureRandom), DB chi luu SHA-256 hex.
 * Moi lan refresh token cu bi thu hoi va thay bang token moi (rotation). Neu mot token
 * da bi thu hoi lai duoc dung -> coi nhu bi danh cap, thu hoi TOAN BO token cua user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /** Ket qua xoay vong: user so huu va refresh token tho moi (chi tra cho client, khong luu). */
    public record Rotation(User user, String newRawToken) {
    }

    @Transactional
    public String issue(User user) {
        return create(user).rawToken();
    }

    /**
     * noRollbackFor: khi phat hien dung lai token, viec thu hoi toan bo token cua user
     * phai duoc commit du request van tra loi 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized(ErrorMessages.REFRESH_TOKEN_INVALID));
        User user = current.getUser();

        if (current.isRevoked()) {
            revokeAllForReuse(user);
        }
        if (current.getExpiresAt().isBefore(now())) {
            refreshTokenRepository.revokeIfActive(current.getId(), null);
            throw ApiException.unauthorized(ErrorMessages.REFRESH_TOKEN_EXPIRED);
        }
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw ApiException.unauthorized(ErrorMessages.ACCOUNT_DISABLED);
        }

        Created next = create(user);
        // Thu hoi co dieu kien: neu request khac vua dung token nay truoc (race), coi la dung lai
        if (refreshTokenRepository.revokeIfActive(current.getId(), next.entity().getId()) == 0) {
            revokeAllForReuse(user);
        }
        return new Rotation(user, next.rawToken());
    }

    /** Idempotent: token khong ton tai hoac da thu hoi thi bo qua. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), null));
    }

    private void revokeAllForReuse(User user) {
        int revoked = refreshTokenRepository.revokeAllByUserId(user.getId());
        log.warn("Refresh token reuse detected for user {} - revoked {} active token(s)", user.getId(), revoked);
        throw ApiException.unauthorized(ErrorMessages.REFRESH_TOKEN_REUSED);
    }

    private Created create(User user) {
        String rawToken = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now().plus(jwtProperties.refreshTokenTtl()));
        token.setRevoked(false);
        return new Created(refreshTokenRepository.save(token), rawToken);
    }

    private record Created(RefreshToken entity, String rawToken) {
    }

    static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
