package com.se183891.badminton_backend.auth;

import com.se183891.badminton_backend.auth.repository.EmailOtpRepository;
import com.se183891.badminton_backend.auth.repository.RefreshTokenRepository;
import com.se183891.badminton_backend.auth.service.OtpMailSender;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chay tren SQL Server that (CourtlyDB_Test). Tu dong bo qua neu khong co DB_PASSWORD.
 */
@SpringBootTest(properties = {
        // Integration test chay tren SQL Server THAT, database rieng CourtlyDB_Test (khong dung H2/SQLite)
        "spring.datasource.url=${TEST_DB_URL:jdbc:sqlserver://localhost:1433;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true}",
        "app.seed.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AuthFlowIntegrationTest {

    private static final String STRONG_PASSWORD = "Courtly123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private EmailOtpRepository emailOtpRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Khong gui email that trong test; lay ma OTP tu tham so truyen vao mock
    @MockitoBean
    private OtpMailSender otpMailSender;

    @BeforeEach
    void cleanDatabase() {
        emailOtpRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ---------- register ----------

    @Test
    void registerReturnsTokensAndUser() throws Exception {
        register("Trần Thị Bình", "Binh@Example.com", "+84 901 234 567", STRONG_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(1800)))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName", is("Trần Thị Bình")))
                .andExpect(jsonPath("$.user.email", is("binh@example.com")))
                .andExpect(jsonPath("$.user.phone", is("0901234567")))
                .andExpect(jsonPath("$.user.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.user.authProvider", is("LOCAL")));
    }

    @Test
    void registerWithoutPhoneTwiceIsAllowed() throws Exception {
        // filtered unique index: nhieu user co phone NULL
        register("A", "a@example.com", null, STRONG_PASSWORD).andExpect(status().isCreated());
        register("B", "b@example.com", "", STRONG_PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void registerDuplicateEmailOrPhoneReturns409() throws Exception {
        register("A", "dup@example.com", "0911111111", STRONG_PASSWORD).andExpect(status().isCreated());

        register("B", "DUP@example.com", null, STRONG_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email đã được sử dụng")));
        register("C", "other@example.com", "+84911111111", STRONG_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Số điện thoại đã được sử dụng")));
    }

    @Test
    void registerValidationReturns400WithFieldErrors() throws Exception {
        registerWithOtp("", "not-an-email", "123", "weakpass", "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/auth/register")))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.otp").exists());

        registerWithOtp("A", "a@example.com", null, " Courtly123", "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------- OTP ----------

    @Test
    void sendOtpStoresOnlyHashAndReturnsTimings() throws Exception {
        sendOtp("Otp@Example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn", is(300)))
                .andExpect(jsonPath("$.resendAfter", is(60)));

        String code = lastOtp("otp@example.com");
        assertThat(code).matches("\\d{6}");
        assertThat(emailOtpRepository.findAll()).singleElement()
                .satisfies(otp -> assertThat(otp.getCodeHash()).isNotEqualTo(code).startsWith("$2"));
    }

    @Test
    void sendOtpRejectsRegisteredEmailAndRespectsCooldown() throws Exception {
        register("A", "taken@example.com", null, STRONG_PASSWORD).andExpect(status().isCreated());
        sendOtp("TAKEN@example.com").andExpect(status().isConflict());

        sendOtp("cool@example.com").andExpect(status().isOk());
        sendOtp("cool@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("giây")));
    }

    @Test
    void registerRequiresCorrectOtp() throws Exception {
        registerWithOtp("A", "noopt@example.com", null, STRONG_PASSWORD, "123456")
                .andExpect(status().isBadRequest());

        sendOtp("wrong@example.com").andExpect(status().isOk());
        String code = lastOtp("wrong@example.com");
        registerWithOtp("A", "wrong@example.com", null, STRONG_PASSWORD, otherCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Mã OTP không đúng, bạn còn 4 lần thử")));
        assertThat(userRepository.existsByEmail("wrong@example.com")).isFalse();

        registerWithOtp("A", "wrong@example.com", null, STRONG_PASSWORD, code)
                .andExpect(status().isCreated());
    }

    @Test
    void otpIsLockedAfterTooManyWrongAttempts() throws Exception {
        sendOtp("lock@example.com").andExpect(status().isOk());
        String code = lastOtp("lock@example.com");
        for (int i = 0; i < 5; i++) {
            registerWithOtp("A", "lock@example.com", null, STRONG_PASSWORD, otherCode(code))
                    .andExpect(status().isBadRequest());
        }
        registerWithOtp("A", "lock@example.com", null, STRONG_PASSWORD, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Bạn đã nhập sai quá nhiều lần, vui lòng yêu cầu mã mới")));
    }

    @Test
    void otpIsKeptWhenRegisterFailsOnDuplicatePhone() throws Exception {
        register("A", "first@example.com", "0933333333", STRONG_PASSWORD).andExpect(status().isCreated());

        sendOtp("second@example.com").andExpect(status().isOk());
        String code = lastOtp("second@example.com");
        registerWithOtp("B", "second@example.com", "0933333333", STRONG_PASSWORD, code)
                .andExpect(status().isConflict());
        // Sua SDT roi dung lai CUNG ma OTP
        registerWithOtp("B", "second@example.com", null, STRONG_PASSWORD, code)
                .andExpect(status().isCreated());
    }

    @Test
    void mailFailureReturns503AndLeavesNoUsableOtp() throws Exception {
        doThrow(ApiException.serviceUnavailable("Không gửi được email, vui lòng thử lại sau"))
                .when(otpMailSender).sendRegistrationOtp(anyString(), anyString(), any(Duration.class));

        sendOtp("fail@example.com").andExpect(status().isServiceUnavailable());
        assertThat(emailOtpRepository.count()).isZero();
    }

    // ---------- login ----------

    @Test
    void loginWithEmailOrPhone() throws Exception {
        register("A", "login@example.com", "0922222222", STRONG_PASSWORD).andExpect(status().isCreated());

        login("LOGIN@example.com", STRONG_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email", is("login@example.com")));
        login("+84 922 222 222", STRONG_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginFailureDoesNotRevealWhetherAccountExists() throws Exception {
        register("A", "exists@example.com", null, STRONG_PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login("exists@example.com", "Wrong999").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody@example.com", "Wrong999").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownUser));
    }

    @Test
    void seedStyleSixCharPasswordCanLogin() throws Exception {
        saveUser("seed@example.com", "123456", Role.CUSTOMER);
        login("seed@example.com", "123456").andExpect(status().isOk());
    }

    // ---------- /users/me ----------

    @Test
    void meRequiresValidToken() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/users/me")));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Token không hợp lệ")));

        JsonNode auth = body(register("Me", "me@example.com", null, STRONG_PASSWORD));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me@example.com")))
                .andExpect(jsonPath("$.authProvider", is("LOCAL")));
    }

    // ---------- refresh / logout ----------

    @Test
    void refreshRotatesAndDetectsReuse() throws Exception {
        JsonNode first = body(register("R", "r@example.com", null, STRONG_PASSWORD));
        String token1 = first.get("refreshToken").asString();

        JsonNode second = body(refresh(token1).andExpect(status().isOk()));
        String token2 = second.get("refreshToken").asString();
        assertThat(token2).isNotEqualTo(token1);

        // Dung lai token1 (da bi thu hoi) -> 401 va thu hoi luon token2
        refresh(token1).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("dùng lại")));
        refresh(token2).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownTokenReturns401() throws Exception {
        refresh("does-not-exist").andExpect(status().isUnauthorized());
        refresh("").andExpect(status().isBadRequest());
    }

    @Test
    void logoutIsIdempotentAndRevokesToken() throws Exception {
        String token = body(register("L", "l@example.com", null, STRONG_PASSWORD)).get("refreshToken").asString();

        postJson("/api/auth/logout", Map.of("refreshToken", token)).andExpect(status().isNoContent());
        postJson("/api/auth/logout", Map.of("refreshToken", token)).andExpect(status().isNoContent());
        postJson("/api/auth/logout", Map.of("refreshToken", "unknown")).andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    // ---------- roles ----------

    @Test
    void adminPingRequiresAdminRole() throws Exception {
        JsonNode customer = body(register("C", "c@example.com", null, STRONG_PASSWORD));
        mvc.perform(get("/api/admin/ping").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        saveUser("admin@example.com", "123456", Role.ADMIN);
        JsonNode admin = body(login("admin@example.com", "123456"));
        mvc.perform(get("/api/admin/ping").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("pong")));

        mvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
    }

    // ---------- google ----------

    @Test
    void googleLoginReturns503WhenDisabled() throws Exception {
        postJson("/api/auth/google", Map.of("idToken", "anything"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", is("Đăng nhập Google chưa được cấu hình")));
    }

    // ---------- helpers ----------

    /** Dang ky day du 2 buoc: gui OTP (neu loi thi tra ve loi do) roi dang ky voi ma nhan duoc. */
    private ResultActions register(String fullName, String email, String phone, String password) throws Exception {
        ResultActions sent = sendOtp(email);
        if (sent.andReturn().getResponse().getStatus() != 200) {
            return sent;
        }
        return registerWithOtp(fullName, email, phone, password, lastOtp(email.trim().toLowerCase()));
    }

    private ResultActions registerWithOtp(String fullName, String email, String phone, String password,
                                          String otp) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", password);
        body.put("otp", otp);
        return postJson("/api/auth/register", body);
    }

    private ResultActions sendOtp(String email) throws Exception {
        return postJson("/api/auth/register/send-otp", Map.of("email", email));
    }

    /** Ma OTP gan nhat da "gui" toi email (lay tu mock OtpMailSender). */
    private String lastOtp(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpMailSender, atLeastOnce()).sendRegistrationOtp(eq(email), code.capture(), any(Duration.class));
        return code.getValue();
    }

    private static String otherCode(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("identifier", identifier, "password", password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(JsonNode auth) {
        return "Bearer " + auth.get("accessToken").asString();
    }

    private void saveUser(String email, String password, Role role) {
        User user = new User();
        user.setFullName("Seed " + role);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String messageOf(String json) {
        return objectMapper.readTree(json).get("message").asString();
    }

}
