package com.se183891.badminton_backend.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.se183891.badminton_backend.auth.dto.AuthResponse;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.config.FirebaseProperties;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Dang nhap Google qua Firebase: app Flutter gui Firebase ID token, backend xac minh
 * bang Firebase Admin SDK roi cap JWT cua Courtly.
 * Khi {@code firebase.enabled=false} (mac dinh) -> 503.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private static final int FULL_NAME_MAX = 100;
    private static final int AVATAR_URL_MAX = 500;

    private final FirebaseProperties firebaseProperties;
    // Chi co bean khi firebase.enabled=true (xem FirebaseConfig)
    private final ObjectProvider<FirebaseAuth> firebaseAuthProvider;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        FirebaseAuth firebaseAuth = firebaseAuthProvider.getIfAvailable();
        if (!firebaseProperties.enabled() || firebaseAuth == null) {
            throw ApiException.serviceUnavailable(ErrorMessages.GOOGLE_DISABLED);
        }

        FirebaseToken token;
        try {
            // TODO(FIREBASE): co the dung verifyIdToken(idToken, true) de kiem tra ca token bi thu hoi
            //  (ton them 1 request toi Firebase moi lan dang nhap).
            token = firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException | IllegalArgumentException ex) {
            log.debug("Firebase ID token rejected: {}", ex.getMessage());
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_TOKEN_INVALID);
        }

        String uid = token.getUid();
        String email = token.getEmail();
        // Chi tin email da xac minh, tranh chiem tai khoan LOCAL bang email gia mao
        if (!StringUtils.hasText(email) || !token.isEmailVerified()) {
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_EMAIL_UNVERIFIED);
        }
        email = AuthService.normalizeEmail(email);

        User user = userRepository.findByFirebaseUid(uid).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                // Email trung tai khoan da co (LOCAL): lien ket, KHONG tao ban ghi moi
                user.setFirebaseUid(uid);
                if (user.getAvatarUrl() == null) {
                    user.setAvatarUrl(truncate(token.getPicture(), AVATAR_URL_MAX));
                }
            } else {
                user = new User();
                user.setFullName(resolveName(token.getName(), email));
                user.setEmail(email);
                user.setRole(Role.CUSTOMER);
                user.setAuthProvider(AuthProvider.GOOGLE);
                user.setFirebaseUid(uid);
                user.setAvatarUrl(truncate(token.getPicture(), AVATAR_URL_MAX));
                user.setEnabled(true);
            }
            // DataIntegrityViolationException (2 request dong thoi) -> 409 o GlobalExceptionHandler
            user = userRepository.saveAndFlush(user);
        }

        if (!user.isEnabled()) {
            throw ApiException.unauthorized(ErrorMessages.ACCOUNT_DISABLED);
        }
        return authService.issueTokens(user);
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
