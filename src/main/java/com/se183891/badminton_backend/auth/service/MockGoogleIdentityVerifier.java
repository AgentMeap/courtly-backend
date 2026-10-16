package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Bo kiem tra token Google GIA, CHI DUNG CHO DEV/DEMO khi chua co Firebase.
 * Bat bang {@code app.google.mock-enabled=true} (GOOGLE_MOCK_ENABLED); khong bao gio chay voi profile prod.
 * <p>
 * Dinh dang token: {@code mock:<email>:<ten hien thi>} (ten co the chua dau hai cham, co the bo trong).
 * Danh tinh tra ve co uid {@code mock:<email>}. GoogleAuthService khong cho danh tinh mock
 * dang nhap vao / lien ket voi tai khoan khong do mock tao.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.google", name = "mock-enabled", havingValue = "true")
public class MockGoogleIdentityVerifier implements GoogleIdentityVerifier {

    public static final String UID_PREFIX = "mock:";

    private static final String TOKEN_PREFIX = "mock";
    /** firebase_uid toi da 128 ky tu (V1), tru di tien to "mock:". */
    private static final int EMAIL_MAX = 128 - UID_PREFIX.length();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s:]+@[^@\\s:]+\\.[^@\\s:]+$");

    public MockGoogleIdentityVerifier(Environment environment) {
        // Lop bao ve thu 2 (lop 1: RequiredSecretsEnvironmentPostProcessor)
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("app.google.mock-enabled=true khong duoc phep khi chay profile prod");
        }
        log.warn("==================================================================");
        log.warn(" MOCK GOOGLE LOGIN ĐANG BẬT - CHỈ DÙNG CHO DEV");
        log.warn(" /api/auth/google chap nhan token gia dang mock:<email>:<ten>");
        log.warn("==================================================================");
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        String[] parts = idToken.trim().split(":", 3);
        if (parts.length < 2 || !TOKEN_PREFIX.equals(parts[0])) {
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_TOKEN_INVALID);
        }
        String email = AuthService.normalizeEmail(parts[1]);
        if (email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
            throw ApiException.unauthorized(ErrorMessages.GOOGLE_TOKEN_INVALID);
        }
        String name = parts.length == 3 && StringUtils.hasText(parts[2]) ? parts[2].trim() : null;
        return new GoogleIdentity(UID_PREFIX + email, email, name, null);
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
