package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.dto.OtpSentResponse;
import com.se183891.badminton_backend.auth.entity.EmailOtp;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.auth.repository.EmailOtpRepository;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.config.OtpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Ma OTP 6 so gui qua email. DB chi luu BCrypt cua ma.
 * Gioi han: het han sau app.otp.ttl, toi da app.otp.max-attempts lan nhap sai,
 * phai cho app.otp.resend-cooldown giua 2 lan gui, toi da app.otp.max-sends-per-hour lan/gio/email.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpMailSender mailSender;
    private final OtpProperties properties;

    /**
     * Tao ma moi (vo hieu ma cu) va gui email. Gui that bai -> rollback, tra 503.
     */
    @Transactional
    public OtpSentResponse send(String email, OtpPurpose purpose) {
        LocalDateTime now = now();

        otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose).ifPresent(last -> {
            long waitSeconds = Duration.between(now, last.getCreatedAt().plus(properties.resendCooldown()))
                    .toSeconds();
            if (waitSeconds > 0) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        ErrorMessages.OTP_RESEND_WAIT.formatted(waitSeconds));
            }
        });
        if (otpRepository.countByEmailAndPurposeAndCreatedAtAfter(email, purpose, now.minusHours(1))
                >= properties.maxSendsPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorMessages.OTP_TOO_MANY_SENDS);
        }

        otpRepository.invalidateActive(email, purpose);

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        EmailOtp otp = new EmailOtp();
        otp.setEmail(email);
        otp.setPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(properties.ttl()));
        otp.setAttempts(0);
        otp.setConsumed(false);
        otpRepository.saveAndFlush(otp);

        mailSender.sendRegistrationOtp(email, code, properties.ttl());

        return new OtpSentResponse(ErrorMessages.OTP_SENT.formatted(email),
                properties.ttl().toSeconds(), properties.resendCooldown().toSeconds());
    }

    /**
     * Kiem tra ma (chua danh dau da dung). Chay trong transaction RIENG va khong rollback
     * khi sai ma, de so lan nhap sai van duoc ghi lai du transaction dang ky bi huy.
     *
     * @return id cua OTP hop le, truyen cho {@link #consume(UUID)}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public UUID verify(String email, OtpPurpose purpose, String code) {
        EmailOtp otp = otpRepository.findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.OTP_NOT_FOUND));

        if (otp.getExpiresAt().isBefore(now())) {
            throw ApiException.badRequest(ErrorMessages.OTP_EXPIRED);
        }
        if (otp.getAttempts() >= properties.maxAttempts()) {
            throw ApiException.badRequest(ErrorMessages.OTP_TOO_MANY_ATTEMPTS);
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otpRepository.incrementAttempts(otp.getId());
            int remaining = properties.maxAttempts() - otp.getAttempts() - 1;
            throw ApiException.badRequest(remaining > 0
                    ? ErrorMessages.OTP_WRONG.formatted(remaining)
                    : ErrorMessages.OTP_TOO_MANY_ATTEMPTS);
        }
        return otp.getId();
    }

    /**
     * Danh dau ma da dung, trong transaction cua nghiep vu goi toi (vd: tao user).
     * Neu tao user that bai thi viec danh dau cung rollback, nguoi dung dung lai duoc ma.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID otpId) {
        if (otpRepository.consumeIfActive(otpId) == 0) {
            throw ApiException.badRequest(ErrorMessages.OTP_NOT_FOUND);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
