package com.se183891.badminton_backend.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Xac minh Firebase ID token bang Firebase Admin SDK. Chi co khi {@code firebase.enabled=true}
 * (bean FirebaseAuth tao trong FirebaseConfig).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FirebaseGoogleIdentityVerifier implements GoogleIdentityVerifier {

    private final FirebaseAuth firebaseAuth;

    @Override
    public GoogleIdentity verify(String idToken) {
        FirebaseToken token;
        try {
            // TODO(FIREBASE): co the dung verifyIdToken(idToken, true) de kiem tra ca token bi thu hoi
            //  (ton them 1 request toi Firebase moi lan dang nhap).
            token = firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException | IllegalArgumentException ex) {
            log.debug("Firebase ID token rejected: {}", ex.getMessage());
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_TOKEN_INVALID);
        }

        // Chi tin email da xac minh, tranh chiem tai khoan LOCAL bang email gia mao
        if (!StringUtils.hasText(token.getEmail()) || !token.isEmailVerified()) {
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_EMAIL_UNVERIFIED);
        }
        return new GoogleIdentity(token.getUid(), token.getEmail(), token.getName(), token.getPicture());
    }
}
