package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.common.exception.ApiException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dung MimeMessage that (chi mock buoc gui SMTP) de bat loi khi dung noi dung email.
 */
class OtpMailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);

    @Test
    void buildsMultipartEmailWithCodeAndSenderName() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        OtpMailSender sender = new OtpMailSender(javaMailSender, "courtly@gmail.com", "app-password", "");

        sender.sendRegistrationOtp("user@example.com", "042517", Duration.ofMinutes(5));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("courtly@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("Courtly");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(message.getSubject()).isEqualTo("Mã xác thực đăng ký tài khoản Courtly");

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        String body = raw.toString(StandardCharsets.UTF_8);
        assertThat(body).contains("multipart/alternative").contains("text/plain").contains("text/html")
                .contains("042517");
    }

    @Test
    void missingCredentialsReturns503WithoutSending() {
        OtpMailSender sender = new OtpMailSender(javaMailSender, "", "", "");

        assertThatThrownBy(() -> sender.sendRegistrationOtp("user@example.com", "123456", Duration.ofMinutes(5)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Chức năng gửi email chưa được cấu hình");
        verify(javaMailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
