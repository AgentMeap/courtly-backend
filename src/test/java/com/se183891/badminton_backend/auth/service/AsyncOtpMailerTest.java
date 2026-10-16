package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AsyncOtpMailerTest {

    private final OtpMailSender mailSender = mock(OtpMailSender.class);
    private final AsyncOtpMailer asyncMailer = new AsyncOtpMailer(mailSender);

    @AfterEach
    void shutdown() {
        asyncMailer.destroy();
    }

    @Test
    void sendsOnBackgroundThreadWhenNoTransaction() {
        asyncMailer.sendAfterCommit("user@example.com", OtpPurpose.RESET_PASSWORD, "123456", Duration.ofMinutes(5));

        verify(mailSender, timeout(2_000)).sendPasswordResetOtp("user@example.com", "123456", Duration.ofMinutes(5));
    }

    @Test
    void mailFailureIsSwallowed() {
        doThrow(ApiException.serviceUnavailable("smtp down"))
                .when(mailSender).sendPasswordResetOtp(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> asyncMailer.sendAfterCommit("user@example.com", OtpPurpose.RESET_PASSWORD,
                "123456", Duration.ofMinutes(5))).doesNotThrowAnyException();
        verify(mailSender, timeout(2_000)).sendPasswordResetOtp(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void masksEmailInLogs() {
        assertThat(AsyncOtpMailer.mask("user@example.com")).isEqualTo("us***@example.com");
        assertThat(AsyncOtpMailer.mask("a@example.com")).isEqualTo("a***@example.com");
        assertThat(AsyncOtpMailer.mask("no-at-sign")).isEqualTo("***");
    }
}
