package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.auth.dto.AuthResponse;
import com.se183891.badminton_backend.auth.service.GoogleIdentityVerifier.GoogleIdentity;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Dang nhap Google: app Flutter gui token, {@link GoogleIdentityVerifier} xac minh
 * (Firebase that, hoac mock khi dev) roi backend cap JWT cua Courtly.
 * Khong co bo kiem tra nao duoc bat (mac dinh) -> 503.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private static final int FULL_NAME_MAX = 100;
    private static final int AVATAR_URL_MAX = 500;

    // Chi co bean khi firebase.enabled=true hoac app.google.mock-enabled=true
    private final ObjectProvider<GoogleIdentityVerifier> verifierProvider;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdentityVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw ApiException.serviceUnavailable(ErrorMessages.GOOGLE_DISABLED);
        }

        GoogleIdentity identity = verifier.verify(idToken);
        String email = AuthService.normalizeEmail(identity.email());
        User user = verifier.isMock()
                ? findOrCreateMockUser(identity, email)
                : findOrLinkUser(identity, email);

        if (!user.isEnabled()) {
            throw ApiException.unauthorized(ErrorMessages.ACCOUNT_DISABLED);
        }
        return authService.issueTokens(user);
    }

    /** Google that (Firebase): tim theo uid, roi theo email (lien ket tai khoan LOCAL), khong co thi tao moi. */
    private User findOrLinkUser(GoogleIdentity identity, String email) {
        User user = userRepository.findByFirebaseUid(identity.uid()).orElse(null);
        if (user != null) {
            return user;
        }
        user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            // Email trung tai khoan da co (LOCAL): lien ket, KHONG tao ban ghi moi
            user.setFirebaseUid(identity.uid());
            if (user.getAvatarUrl() == null) {
                user.setAvatarUrl(truncate(identity.picture(), AVATAR_URL_MAX));
            }
        } else {
            user = newGoogleCustomer(identity, email);
        }
        // DataIntegrityViolationException (2 request dong thoi) -> 409 o GlobalExceptionHandler
        return userRepository.saveAndFlush(user);
    }

    /**
     * Google mock (dev): chi dang nhap tai khoan do chinh mock tao (CUSTOMER, GOOGLE, uid "mock:...").
     * KHONG BAO GIO dang nhap vao hay lien ket voi tai khoan khac co cung email (LOCAL, ADMIN, Google that).
     */
    private User findOrCreateMockUser(GoogleIdentity identity, String email) {
        User user = userRepository.findByFirebaseUid(identity.uid()).orElse(null);
        if (user != null) {
            if (!isMockCreated(user) || !email.equals(user.getEmail())) {
                throw ApiException.conflict(ErrorMessages.GOOGLE_EMAIL_USED_BY_OTHER_ACCOUNT);
            }
            return user;
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict(ErrorMessages.GOOGLE_EMAIL_USED_BY_OTHER_ACCOUNT);
        }
        return userRepository.saveAndFlush(newGoogleCustomer(identity, email));
    }

    private static boolean isMockCreated(User user) {
        return user.getAuthProvider() == AuthProvider.GOOGLE
                && user.getRole() == Role.CUSTOMER
                && user.getPasswordHash() == null
                && user.getFirebaseUid() != null
                && user.getFirebaseUid().startsWith(MockGoogleIdentityVerifier.UID_PREFIX);
    }

    private static User newGoogleCustomer(GoogleIdentity identity, String email) {
        User user = new User();
        user.setFullName(resolveName(identity.name(), email));
        user.setEmail(email);
        user.setRole(Role.CUSTOMER);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setFirebaseUid(identity.uid());
        user.setAvatarUrl(truncate(identity.picture(), AVATAR_URL_MAX));
        user.setEnabled(true);
        return user;
    }

    private static String resolveName(String name, String email) {
        String value = StringUtils.hasText(name) ? name.trim() : email.substring(0, email.indexOf('@'));
        return truncate(value, FULL_NAME_MAX);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
