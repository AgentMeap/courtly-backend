package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.dto.AuthResponse;
import com.se183891.badminton_backend.auth.dto.LoginRequest;
import com.se183891.badminton_backend.auth.dto.OtpSentResponse;
import com.se183891.badminton_backend.auth.dto.PhoneNumbers;
import com.se183891.badminton_backend.auth.dto.RefreshTokenRequest;
import com.se183891.badminton_backend.auth.dto.RegisterRequest;
import com.se183891.badminton_backend.auth.dto.SendOtpRequest;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.common.exception.UniqueViolations;
import com.se183891.badminton_backend.security.JwtService;
import com.se183891.badminton_backend.user.dto.UserDto;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    /** BCrypt chi xu ly toi da 72 byte. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    /** Hash gia de so sanh khi user khong ton tai, giu thoi gian phan hoi dong deu. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService,
                       OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Buoc 1 dang ky: gui ma OTP toi email (email chua duoc dung). */
    public OtpSentResponse sendRegisterOtp(SendOtpRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
        }
        return otpService.send(email, OtpPurpose.REGISTER);
    }

    /** Buoc 2 dang ky: chi tao tai khoan khi ma OTP dung. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = PhoneNumbers.normalize(request.phone());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.PASSWORD_TOO_LONG);
        }

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw ApiException.conflict(ErrorMessages.PHONE_TAKEN);
        }

        // Kiem tra OTP sau cac rang buoc khac, de loi trung email/SDT khong lam ton luot nhap
        otpService.consume(otpService.verify(email, OtpPurpose.REGISTER, request.otp()));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);

        try {
            // flush ngay de unique index bat truong hop 2 request dang ky dong thoi
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(UniqueViolations.messageFor(ex));
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = findByIdentifier(request.identifier());
        String hash = found.map(User::getPasswordHash).orElse(null);

        // Luon chay BCrypt (ke ca khi khong co user) va tra cung mot thong bao,
        // de khong tiet lo email/SDT co ton tai hay khong.
        boolean matches = passwordMatches(request.password(), hash != null ? hash : dummyPasswordHash);
        if (found.isEmpty() || hash == null || !matches) {
            throw ApiException.unauthorized(ErrorMessages.BAD_CREDENTIALS);
        }
        User user = found.get();
        if (!user.isEnabled()) {
            throw ApiException.unauthorized(ErrorMessages.ACCOUNT_DISABLED);
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        return buildResponse(rotation.user(), rotation.newRawToken());
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** Cap access token + refresh token moi cho user (dung chung cho dang nhap Google). */
    @Transactional
    public AuthResponse issueTokens(User user) {
        return buildResponse(user, refreshTokenService.issue(user));
    }

    private AuthResponse buildResponse(User user, String rawRefreshToken) {
        return new AuthResponse(jwtService.generateAccessToken(user), rawRefreshToken,
                AuthResponse.BEARER, jwtService.accessTokenTtlSeconds(), UserDto.from(user));
    }

    private Optional<User> findByIdentifier(String identifier) {
        String value = identifier.trim();
        if (value.contains("@")) {
            return userRepository.findByEmail(normalizeEmail(value));
        }
        String phone = PhoneNumbers.normalize(value);
        return phone == null ? Optional.empty() : userRepository.findByPhone(phone);
    }

    private boolean passwordMatches(String raw, String hash) {
        try {
            return passwordEncoder.matches(raw, hash);
        } catch (IllegalArgumentException ex) {
            // BCrypt tu choi mat khau > 72 byte (vd: chuoi Unicode dai) -> coi nhu sai mat khau
            return false;
        }
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
