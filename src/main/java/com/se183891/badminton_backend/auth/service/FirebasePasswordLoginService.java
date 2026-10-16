package com.se183891.badminton_backend.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.config.FirebaseProperties;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Dang nhap email + mat khau cho tai khoan luu o Firebase (tao qua /api/auth/register/firebase).
 * Admin SDK khong kiem tra duoc mat khau, nen goi REST API cua Firebase Authentication
 * (accounts:signInWithPassword, can FIREBASE_WEB_API_KEY), xac minh ID token tra ve roi
 * tim / lien ket / tao ban ghi {@code users} trong SQL de backend cap JWT cua Courtly.
 * Mat khau KHONG duoc luu vao SQL: lan dang nhap nao cung do Firebase kiem tra.
 */
@Slf4j
@Service
public class FirebasePasswordLoginService {

    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={key}";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };
    /** Ma loi cua Firebase nghia la sai email / mat khau (hoac tai khoan Firebase bi khoa). */
    private static final Set<String> BAD_CREDENTIAL_CODES = Set.of(
            "INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_EMAIL", "USER_DISABLED");
    private static final String TOO_MANY_ATTEMPTS_CODE = "TOO_MANY_ATTEMPTS_TRY_LATER";
    private static final int FULL_NAME_MAX = 100;

    // Chi co bean khi firebase.enabled=true
    private final ObjectProvider<FirebaseAuth> authProvider;
    private final FirebaseProperties properties;
    private final UserRepository userRepository;
    private final RestClient restClient;

    public FirebasePasswordLoginService(ObjectProvider<FirebaseAuth> authProvider,
                                        FirebaseProperties properties, UserRepository userRepository) {
        this.authProvider = authProvider;
        this.properties = properties;
        this.userRepository = userRepository;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Tra ve user SQL ung voi tai khoan Firebase neu email + mat khau dung.
     * Empty: Firebase tat, chua dat FIREBASE_WEB_API_KEY, hoac sai email / mat khau.
     * 429 neu Firebase khoa tam vi sai qua nhieu lan, 503 neu khong goi duoc Firebase.
     */
    public Optional<User> login(String email, String password) {
        FirebaseAuth auth = authProvider.getIfAvailable();
        if (auth == null) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(properties.webApiKey())) {
            log.warn("firebase.enabled=true nhung chua dat FIREBASE_WEB_API_KEY: "
                    + "/api/auth/login khong dang nhap duoc tai khoan Firebase");
            return Optional.empty();
        }

        String idToken = signIn(email, password);
        if (idToken == null) {
            return Optional.empty();
        }
        FirebaseToken token;
        try {
            token = auth.verifyIdToken(idToken);
        } catch (FirebaseAuthException ex) {
            log.error("Khong xac minh duoc ID token Firebase vua cap", ex);
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
        }
        // Chi tin email da xac minh (tai khoan do backend tao sau OTP luon la verified),
        // tranh chiem tai khoan SQL trung email bang tai khoan Firebase tu tao.
        if (!StringUtils.hasText(token.getEmail()) || !token.isEmailVerified()) {
            log.warn("Tu choi dang nhap Firebase uid {}: email chua xac minh", token.getUid());
            return Optional.empty();
        }
        return findOrLinkUser(token);
    }

    /** Goi Firebase kiem tra mat khau. Tra ve ID token, hoac null neu sai email / mat khau. */
    private String signIn(String email, String password) {
        Map<String, Object> body = Map.of("email", email, "password", password, "returnSecureToken", true);
        try {
            return restClient.post()
                    .uri(SIGN_IN_URL, properties.webApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        Map<String, Object> json = response.bodyTo(JSON_MAP);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            if (json != null && json.get("idToken") instanceof String idToken) {
                                return idToken;
                            }
                            log.error("Firebase signInWithPassword khong tra ve idToken");
                            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
                        }
                        String code = errorCode(json);
                        if (BAD_CREDENTIAL_CODES.contains(code)) {
                            return null;
                        }
                        if (TOO_MANY_ATTEMPTS_CODE.equals(code)) {
                            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                                    ErrorMessages.LOGIN_TOO_MANY_ATTEMPTS);
                        }
                        // Vd API key sai / bi gioi han, chua bat Email/Password trong Firebase Console
                        log.error("Firebase signInWithPassword loi {}: {}", response.getStatusCode(), code);
                        throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
                    });
        } catch (RestClientException ex) {
            log.error("Khong goi duoc Firebase signInWithPassword", ex);
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
        }
    }

    /** Lay ma loi tu {"error":{"message":"CODE : chi tiet"}}. */
    private static String errorCode(Map<String, Object> json) {
        if (json != null && json.get("error") instanceof Map<?, ?> error
                && error.get("message") instanceof String message) {
            return message.split(":", 2)[0].trim();
        }
        return "";
    }

    /** Tim theo uid, roi theo email (lien ket tai khoan chua co mat khau SQL), khong co thi tao moi. */
    private Optional<User> findOrLinkUser(FirebaseToken token) {
        String email = AuthService.normalizeEmail(token.getEmail());
        User user = userRepository.findByFirebaseUid(token.getUid()).orElse(null);
        if (user != null) {
            return Optional.of(user);
        }
        user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            if (user.getFirebaseUid() != null) {
                log.warn("Email {} da lien ket voi tai khoan Firebase khac", email);
                return Optional.empty();
            }
            user.setFirebaseUid(token.getUid());
        } else {
            user = new User();
            user.setFullName(resolveName(token.getName(), email));
            user.setEmail(email);
            user.setRole(Role.CUSTOMER);
            user.setAuthProvider(AuthProvider.LOCAL);
            user.setFirebaseUid(token.getUid());
            user.setEnabled(true);
        }
        // DataIntegrityViolationException (2 request dong thoi) -> 409 o GlobalExceptionHandler
        return Optional.of(userRepository.saveAndFlush(user));
    }

    private static String resolveName(String name, String email) {
        String value = StringUtils.hasText(name) ? name.trim() : email.substring(0, email.indexOf('@'));
        return value.length() <= FULL_NAME_MAX ? value : value.substring(0, FULL_NAME_MAX);
    }
}
