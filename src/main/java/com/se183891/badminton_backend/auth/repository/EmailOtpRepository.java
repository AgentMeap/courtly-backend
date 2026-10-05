package com.se183891.badminton_backend.auth.repository;

import com.se183891.badminton_backend.auth.entity.EmailOtp;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    /** Ma gui gan nhat (ke ca da dung) - de tinh thoi gian cho gui lai. */
    Optional<EmailOtp> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    /** Ma con hieu luc gan nhat. */
    Optional<EmailOtp> findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(String email,
                                                                                      OtpPurpose purpose);

    long countByEmailAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, LocalDateTime after);

    /** Vo hieu cac ma cu khi gui ma moi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailOtp o set o.consumed = true "
            + "where o.email = :email and o.purpose = :purpose and o.consumed = false")
    int invalidateActive(@Param("email") String email, @Param("purpose") OtpPurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailOtp o set o.attempts = o.attempts + 1 where o.id = :id")
    int incrementAttempts(@Param("id") UUID id);

    /** Danh dau da dung (atomic): tra ve 0 neu request khac vua dung ma nay truoc. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailOtp o set o.consumed = true where o.id = :id and o.consumed = false")
    int consumeIfActive(@Param("id") UUID id);
}
