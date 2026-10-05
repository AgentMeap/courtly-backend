package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gui email chua ma OTP qua SMTP (mac dinh Gmail). Khong bao gio ghi ma OTP ra log.
 * Chua cau hinh MAIL_USERNAME / MAIL_PASSWORD -> 503.
 */
@Slf4j
@Component
public class OtpMailSender {

    private static final String SENDER_NAME = "Courtly";

    private final JavaMailSender mailSender;
    private final String username;
    private final String password;
    private final String from;

    public OtpMailSender(JavaMailSender mailSender,
                         @Value("${spring.mail.username:}") String username,
                         @Value("${spring.mail.password:}") String password,
                         @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.from = StringUtils.hasText(from) ? from : username;
    }

    public void sendRegistrationOtp(String to, String code, Duration ttl) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw ApiException.serviceUnavailable(ErrorMessages.MAIL_DISABLED);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart = true: bat buoc khi gui ca ban text thuong lan HTML (setText(plain, html))
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject("Mã xác thực đăng ký tài khoản Courtly");
            helper.setText(plainText(code, ttl), html(code, ttl));
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.error("Failed to send registration OTP email: {}", ex.getMessage());
            throw ApiException.serviceUnavailable(ErrorMessages.MAIL_SEND_FAILED);
        }
    }

    private static String plainText(String code, Duration ttl) {
        return """
                Mã xác thực đăng ký Courtly của bạn là: %s
                Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.
                Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.
                """.formatted(code, ttl.toMinutes());
    }

    private static String html(String code, Duration ttl) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2933">
                  <h2 style="color:#0f766e">Courtly</h2>
                  <p>Mã xác thực đăng ký tài khoản của bạn là:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;margin:16px 0">%s</p>
                  <p>Mã có hiệu lực trong <b>%d phút</b>. Không chia sẻ mã này với bất kỳ ai.</p>
                  <p style="color:#6b7280;font-size:13px">Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.</p>
                </div>
                """.formatted(code, ttl.toMinutes());
    }
}
