package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * Gui email OTP tren thread rieng, SAU KHI transaction tao ma da commit, de response tra ve ngay
 * (khong cho SMTP) - nho vay thoi gian phan hoi khong phan biet email co that hay khong.
 * Loi gui mail chi duoc log (khong log ma OTP, email bi che bot), khong anh huong response.
 * <p>
 * Executor rieng, khong dang ky thanh bean, de khong thay the applicationTaskExecutor mac dinh cua Spring Boot.
 */
@Slf4j
@Component
public class AsyncOtpMailer implements DisposableBean {

    private final OtpMailSender mailSender;
    private final ThreadPoolTaskExecutor executor;

    public AsyncOtpMailer(OtpMailSender mailSender) {
        this.mailSender = mailSender;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("otp-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
    }

    /**
     * Len lich gui mail sau khi transaction hien tai commit (neu rollback thi khong gui).
     * Khong co transaction thi dua vao hang doi ngay.
     */
    public void sendAfterCommit(String email, OtpPurpose purpose, String code, Duration ttl) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(email, purpose, code, ttl);
                }
            });
        } else {
            submit(email, purpose, code, ttl);
        }
    }

    private void submit(String email, OtpPurpose purpose, String code, Duration ttl) {
        try {
            executor.execute(() -> deliver(email, purpose, code, ttl));
        } catch (RuntimeException ex) {
            // Hang doi day / dang tat: bo qua, nguoi dung co the yeu cau ma moi sau cooldown
            log.error("Could not queue {} OTP email to {}: {}", purpose, mask(email), ex.getClass().getSimpleName());
        }
    }

    private void deliver(String email, OtpPurpose purpose, String code, Duration ttl) {
        try {
            switch (purpose) {
                case REGISTER -> mailSender.sendRegistrationOtp(email, code, ttl);
                case RESET_PASSWORD -> mailSender.sendPasswordResetOtp(email, code, ttl);
            }
        } catch (RuntimeException ex) {
            log.error("Async {} OTP email to {} failed: {}", purpose, mask(email), ex.getMessage());
        }
    }

    /** "user@example.com" -> "us***@example.com". */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
