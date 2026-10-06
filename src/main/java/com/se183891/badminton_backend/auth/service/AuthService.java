package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.dto.AuthResponse;
import com.se183891.badminton_backend.auth.dto.ForgotPasswordRequest;
import com.se183891.badminton_backend.auth.dto.LoginRequest;
import com.se183891.badminton_backend.auth.dto.OtpSentResponse;
import com.se183891.badminton_backend.auth.dto.PhoneNumbers;
import com.se183891.badminton_backend.auth.dto.RefreshTokenRequest;
import com.se183891.badminton_backend.auth.dto.RegisterRequest;
import com.se183891.badminton_backend.auth.dto.ResetPasswordRequest;
import com.se183891.badminton_backend.auth.dto.SendOtpRequest;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.common.exception.UniqueViolations;
import com.se183891.badminton_backend.common.response.MessageResponse;
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
    private final OtpMailSender otpMailSender;
    /** Hash gia de so sanh khi user khong ton tai, giu thoi gian phan hoi dong deu. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService,
                       OtpService otpService, OtpMailSender otpMailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.otpMailSender = otpMailSender;
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
        requireBcryptLength(request.password());

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

    /**
     * Quen mat khau, buoc 1: luon tra cung mot phan hoi, khong tiet lo email co ton tai hay khong.
     * Chi thuc su tao + gui OTP khi email thuoc tai khoan dat lai mat khau duoc (LOCAL, enabled, co mat khau).
     */
    public OtpSentResponse forgotPassword(ForgotPasswordRequest request) {
        // Kiem tra cau hinh mail cho MOI email, de loi 503 khong phan biet email co that hay khong
        otpMailSender.ensureConfigured();
        String email = normalizeEmail(request.email());
        if (findResettableUser(email).isPresent()) {
            otpService.send(email, OtpPurpose.RESET_PASSWORD);
        }
        return otpService.sentResponse(ErrorMessages.PASSWORD_RESET_OTP_SENT);
    }

    /**
     * Quen mat khau, buoc 2: dung ma OTP thi doi mat khau va thu hoi MOI refresh token cua user
     * (buoc dang nhap lai tren moi thiet bi). Khong cap token moi.
     * Moi loi OTP / email khong hop le deu tra cung mot thong bao 400.
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        requireBcryptLength(request.newPassword());

        Optional<User> found = findResettableUser(email);
        if (found.isEmpty()) {
            // Chay BCrypt nhu khi kiem tra ma that, giu thoi gian phan hoi dong deu
            passwordMatches(request.otp(), dummyPasswordHash);
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RESET_OTP_INVALID);
        }
        try {
            // verify chay transaction rieng: lan nhap sai van duoc dem du request nay that bai
            otpService.consume(otpService.verify(email, OtpPurpose.RESET_PASSWORD, request.otp()));
        } catch (ApiException ex) {
            throw ApiException.badRequest(ErrorMessages.PASSWORD_RESET_OTP_INVALID);
        }

        // consume() xoa persistence context nen user co the da detached: saveAndFlush (merge) de ghi lai
        User user = found.get();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user = userRepository.saveAndFlush(user);
        refreshTokenService.revokeAll(user);
        return new MessageResponse(ErrorMessages.PASSWORD_RESET_SUCCESS);
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

    /** Tai khoan dat lai mat khau duoc: LOCAL, dang enabled va co mat khau (tai khoan Google-only thi khong). */
    private Optional<User> findResettableUser(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getAuthProvider() == AuthProvider.LOCAL
                        && user.isEnabled()
                        && user.getPasswordHash() != null);
    }

    private static void requireBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.PASSWORD_TOO_LONG);
        }
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
