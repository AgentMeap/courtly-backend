package com.se183891.badminton_backend.auth.repository;

import com.se183891.badminton_backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hoi co dieu kien (atomic). Tra ve 0 neu token da bi thu hoi truoc do,
     * nho vay 2 request refresh dong thoi voi cung token khong the cung thanh cong.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true, t.replacedBy = :replacedBy "
            + "where t.id = :id and t.revoked = false")
    int revokeIfActive(@Param("id") UUID id, @Param("replacedBy") UUID replacedBy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);
}
