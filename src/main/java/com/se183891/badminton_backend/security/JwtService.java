package com.se183891.badminton_backend.security;

import com.se183891.badminton_backend.config.JwtProperties;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Tao va kiem tra access token JWT (HS256). Claims: sub = userId, email, role.
 */
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET phai dai it nhat " + MIN_SECRET_BYTES + " byte (HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /**
     * @throws TokenValidationException neu token sai chu ky, sai dinh dang hoac het han
     */
    public AuthUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (ExpiredJwtException ex) {
            throw new TokenValidationException(true);
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw new TokenValidationException(false);
        }
    }

    public static class TokenValidationException extends RuntimeException {
        private final boolean expired;

        TokenValidationException(boolean expired) {
            super(expired ? "Access token expired" : "Access token invalid");
            this.expired = expired;
        }

        public boolean isExpired() {
            return expired;
        }
    }
}
