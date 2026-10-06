package com.se183891.badminton_backend.auth;

import com.se183891.badminton_backend.auth.entity.EmailOtp;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.auth.repository.EmailOtpRepository;
import com.se183891.badminton_backend.auth.repository.RefreshTokenRepository;
import com.se183891.badminton_backend.auth.service.OtpMailSender;
import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quen mat khau bang OTP email. Chay tren SQL Server that (CourtlyDB_Test), tu bo qua neu khong co DB_PASSWORD.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:sqlserver://localhost:1433;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true}",
        "app.seed.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";
    private static final String GENERIC_SENT = "Nếu email đã đăng ký, mã OTP đặt lại mật khẩu đã được gửi tới email đó";
    private static final String GENERIC_INVALID = "Mã OTP không đúng hoặc đã hết hạn";

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Khong gui email that; lay ma OTP tu tham so truyen vao mock
    @MockitoBean
    private OtpMailSender otpMailSender;

    @BeforeEach
    void cleanDatabase() {
        emailOtpRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ---------- forgot ----------

    @Test
    void forgotForRegisteredEmailSendsResetMail() throws Exception {
        saveLocalUser("user@example.com", OLD_PASSWORD, Role.CUSTOMER);

        forgot("User@Example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is(GENERIC_SENT)))
                .andExpect(jsonPath("$.expiresIn", is(300)))
                .andExpect(jsonPath("$.resendAfter", is(60)));

        assertThat(lastResetOtp("user@example.com")).matches("\\d{6}");
        verify(otpMailSender, never()).sendRegistrationOtp(anyString(), anyString(), any(Duration.class));
        assertThat(emailOtpRepository.findAll()).singleElement()
                .satisfies(otp -> assertThat(otp.getPurpose()).isEqualTo(OtpPurpose.RESET_PASSWORD));
    }

    @Test
    void forgotForUnknownEmailLooksTheSameButSendsNothing() throws Exception {
        saveLocalUser("known@example.com", OLD_PASSWORD, Role.CUSTOMER);

        String known = forgot("known@example.com").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String unknown = forgot("nobody@example.com").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(unknown)).isEqualTo(objectMapper.readTree(known));
        verify(otpMailSender, never()).sendPasswordResetOtp(eq("nobody@example.com"), anyString(), any());
        assertThat(emailOtpRepository.findAll()).extracting(EmailOtp::getEmail).containsOnly("known@example.com");
    }

    @Test
    void forgotForGoogleOnlyAccountSendsNothing() throws Exception {
        User google = new User();
        google.setFullName("Google User");
        google.setEmail("google@example.com");
        google.setRole(Role.CUSTOMER);
        google.setAuthProvider(AuthProvider.GOOGLE);
        google.setFirebaseUid("firebase-uid-1");
        google.setEnabled(true);
        userRepository.save(google);

        forgot("google@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is(GENERIC_SENT)));
        verify(otpMailSender, never()).sendPasswordResetOtp(anyString(), anyString(), any());
        assertThat(emailOtpRepository.count()).isZero();
    }

    @Test
    void forgotResendTooFastIsSilentlyThrottled() throws Exception {
        saveLocalUser("fast@example.com", OLD_PASSWORD, Role.CUSTOMER);

        JsonNode first = body(forgot("fast@example.com").andExpect(status().isOk()));
        // Lan 2 trong cooldown: van 200, body giong het, nhung khong tao ma moi va khong gui mail
        JsonNode second = body(forgot("fast@example.com").andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        verify(otpMailSender, times(1)).sendPasswordResetOtp(eq("fast@example.com"), anyString(), any());
        assertThat(emailOtpRepository.count()).isEqualTo(1);
    }

    @Test
    void forgotOverHourlyLimitLooksIdenticalForRealAndUnknownEmail() throws Exception {
        saveLocalUser("limit@example.com", OLD_PASSWORD, Role.CUSTOMER);
        JsonNode expected = body(forgot("nobody@example.com").andExpect(status().isOk()));

        for (int i = 0; i < 6; i++) {
            assertThat(body(forgot("limit@example.com").andExpect(status().isOk()))).isEqualTo(expected);
            assertThat(body(forgot("nobody@example.com").andExpect(status().isOk()))).isEqualTo(expected);
            // Dua cac ma da gui lui 61 giay de qua cooldown (60s) nhung van trong cua so 1 gio
            jdbcTemplate.update("UPDATE email_otps SET created_at = DATEADD(second, -61, created_at)");
        }

        // Toi da 5 lan gui/gio: lan thu 6 bi chan that (khong mail, khong ban ghi), chi la khong bao loi
        verify(otpMailSender, times(5)).sendPasswordResetOtp(eq("limit@example.com"), anyString(), any());
        verify(otpMailSender, never()).sendPasswordResetOtp(eq("nobody@example.com"), anyString(), any());
        assertThat(emailOtpRepository.count()).isEqualTo(5);
    }

    // ---------- reset ----------

    @Test
    void resetWithCorrectCodeChangesPasswordAndRevokesSessions() throws Exception {
        saveLocalUser("reset@example.com", OLD_PASSWORD, Role.CUSTOMER);
        LocalDateTime updatedBefore = userRepository.findByEmail("reset@example.com").orElseThrow().getUpdatedAt();
        String oldRefresh = body(login("reset@example.com", OLD_PASSWORD).andExpect(status().isOk()))
                .get("refreshToken").asString();

        forgot("reset@example.com").andExpect(status().isOk());
        reset("RESET@example.com", lastResetOtp("reset@example.com"), NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Đặt lại mật khẩu thành công")))
                // Khong tu dang nhap sau khi dat lai
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        login("reset@example.com", NEW_PASSWORD).andExpect(status().isOk());
        login("reset@example.com", OLD_PASSWORD).andExpect(status().isUnauthorized());
        // Refresh token cap truoc khi dat lai da bi thu hoi
        refresh(oldRefresh).andExpect(status().isUnauthorized());

        User user = userRepository.findByEmail("reset@example.com").orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getUpdatedAt()).isAfter(updatedBefore);
    }

    @Test
    void resetWithWrongCodeReturnsGeneric400AndCountsAttempts() throws Exception {
        saveLocalUser("wrong@example.com", OLD_PASSWORD, Role.CUSTOMER);
        forgot("wrong@example.com").andExpect(status().isOk());
        String code = lastResetOtp("wrong@example.com");

        reset("wrong@example.com", otherCode(code), NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(GENERIC_INVALID)));
        assertThat(emailOtpRepository.findAll()).singleElement()
                .satisfies(otp -> assertThat(otp.getAttempts()).isEqualTo(1));
        login("wrong@example.com", OLD_PASSWORD).andExpect(status().isOk());

        // Ma dung van con dung duoc sau 1 lan sai
        reset("wrong@example.com", code, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetIsLockedAfterFiveWrongAttempts() throws Exception {
        saveLocalUser("lock@example.com", OLD_PASSWORD, Role.CUSTOMER);
        forgot("lock@example.com").andExpect(status().isOk());
        String code = lastResetOtp("lock@example.com");

        for (int i = 0; i < 5; i++) {
            reset("lock@example.com", otherCode(code), NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        reset("lock@example.com", code, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(GENERIC_INVALID)));
        login("lock@example.com", OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetForUnknownEmailReturnsSameErrorAsWrongCode() throws Exception {
        reset("ghost@example.com", "123456", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(GENERIC_INVALID)));
    }

    @Test
    void resetRejectsWeakPasswordWithoutUsingTheCode() throws Exception {
        saveLocalUser("weak@example.com", OLD_PASSWORD, Role.CUSTOMER);
        forgot("weak@example.com").andExpect(status().isOk());
        String code = lastResetOtp("weak@example.com");

        for (String weak : new String[]{"short1A", "nouppercase1", "NoDigitsHere", " Spaced123"}) {
            reset("weak@example.com", code, weak)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        }
        // Mat khau qua 72 byte (ky tu Unicode nhieu byte) -> 400
        reset("weak@example.com", code, "A1" + "ệ".repeat(30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Mật khẩu quá dài (tối đa 72 byte)")));

        assertThat(emailOtpRepository.findAll()).singleElement()
                .satisfies(otp -> {
                    assertThat(otp.getAttempts()).isZero();
                    assertThat(otp.isConsumed()).isFalse();
                });
        reset("weak@example.com", code, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void usedCodeCannotBeReused() throws Exception {
        saveLocalUser("reuse@example.com", OLD_PASSWORD, Role.CUSTOMER);
        forgot("reuse@example.com").andExpect(status().isOk());
        String code = lastResetOtp("reuse@example.com");

        reset("reuse@example.com", code, NEW_PASSWORD).andExpect(status().isOk());
        reset("reuse@example.com", code, "Another789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(GENERIC_INVALID)));
        login("reuse@example.com", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void registerCodeCannotResetPassword() throws Exception {
        sendRegisterOtp("cross@example.com").andExpect(status().isOk());
        String registerCode = lastRegisterOtp("cross@example.com");
        saveLocalUser("cross@example.com", OLD_PASSWORD, Role.CUSTOMER);

        reset("cross@example.com", registerCode, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(GENERIC_INVALID)));
        login("cross@example.com", OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetCodeCannotBeUsedToRegister() throws Exception {
        saveLocalUser("cross2@example.com", OLD_PASSWORD, Role.CUSTOMER);
        forgot("cross2@example.com").andExpect(status().isOk());
        String resetCode = lastResetOtp("cross2@example.com");
        // Xoa user de buoc dang ky di toi phan kiem tra OTP (neu khong se 409 vi trung email)
        userRepository.deleteAllInBatch();

        Map<String, Object> registerBody = new HashMap<>();
        registerBody.put("fullName", "Cross");
        registerBody.put("email", "cross2@example.com");
        registerBody.put("password", NEW_PASSWORD);
        registerBody.put("otp", resetCode);
        postJson("/api/auth/register", registerBody).andExpect(status().isBadRequest());
        assertThat(userRepository.existsByEmail("cross2@example.com")).isFalse();
    }

    // ---------- helpers ----------

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/auth/password/forgot", Map.of("email", email));
    }

    private ResultActions reset(String email, String otp, String newPassword) throws Exception {
        return postJson("/api/auth/password/reset", Map.of("email", email, "otp", otp, "newPassword", newPassword));
    }

    private ResultActions sendRegisterOtp(String email) throws Exception {
        return postJson("/api/auth/register/send-otp", Map.of("email", email));
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

    /** Ma dat lai mat khau gan nhat da "gui" toi email (lay tu mock OtpMailSender). */
    private String lastResetOtp(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpMailSender, atLeastOnce()).sendPasswordResetOtp(eq(email), code.capture(), any(Duration.class));
        return code.getValue();
    }

    private String lastRegisterOtp(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpMailSender, atLeastOnce()).sendRegistrationOtp(eq(email), code.capture(), any(Duration.class));
        return code.getValue();
    }

    private static String otherCode(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    private void saveLocalUser(String email, String password, Role role) {
        User user = new User();
        user.setFullName("Seed " + role);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        userRepository.save(user);
    }
}
