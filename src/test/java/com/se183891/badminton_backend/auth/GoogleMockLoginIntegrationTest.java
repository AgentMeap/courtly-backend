package com.se183891.badminton_backend.auth;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/auth/google voi Google mock bat (app.google.mock-enabled=true). Truong hop mock TAT -> 503 nam o
 * AuthFlowIntegrationTest#googleLoginReturns503WhenDisabled. Chay tren SQL Server that, tu bo qua neu khong co DB_PASSWORD.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:sqlserver://localhost:1433;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true}",
        "app.seed.enabled=false",
        "spring.jpa.show-sql=false",
        "app.google.mock-enabled=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class GoogleMockLoginIntegrationTest {

    private static final String CONFLICT_MESSAGE = "Email đã được dùng bởi tài khoản khác";

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

    @MockitoBean
    private OtpMailSender otpMailSender;

    @BeforeEach
    void cleanDatabase() {
        emailOtpRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void validMockTokenCreatesGoogleCustomer() throws Exception {
        JsonNode auth = body(google("mock:Demo@Example.com:Người Dùng Demo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email", is("demo@example.com")))
                .andExpect(jsonPath("$.user.fullName", is("Người Dùng Demo")))
                .andExpect(jsonPath("$.user.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.user.authProvider", is("GOOGLE"))));

        User user = userRepository.findByEmail("demo@example.com").orElseThrow();
        assertThat(user.getFirebaseUid()).isEqualTo("mock:demo@example.com");
        assertThat(user.getPasswordHash()).isNull();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + auth.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("demo@example.com")));
    }

    @Test
    void secondLoginWithSameEmailReturnsSameUser() throws Exception {
        String firstId = body(google("mock:again@example.com:Lần Một")).get("user").get("id").asString();
        String secondId = body(google("mock:AGAIN@example.com:Tên Khác").andExpect(status().isOk()))
                .get("user").get("id").asString();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void mockCannotTakeOverAdminAccount() throws Exception {
        saveLocalUser("admin@example.com", Role.ADMIN);

        google("mock:admin@example.com:Hacker")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is(CONFLICT_MESSAGE)));
        assertThat(userRepository.findByEmail("admin@example.com").orElseThrow().getFirebaseUid()).isNull();
    }

    @Test
    void mockCannotTakeOverLocalAccount() throws Exception {
        saveLocalUser("local@example.com", Role.CUSTOMER);

        google("mock:local@example.com:Hacker")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is(CONFLICT_MESSAGE)));
        assertThat(userRepository.findByEmail("local@example.com").orElseThrow().getFirebaseUid()).isNull();
    }

    @Test
    void mockCannotTakeOverRealGoogleAccount() throws Exception {
        User real = new User();
        real.setFullName("Real Google");
        real.setEmail("real@example.com");
        real.setRole(Role.CUSTOMER);
        real.setAuthProvider(AuthProvider.GOOGLE);
        real.setFirebaseUid("AbCdEf123456");
        real.setEnabled(true);
        userRepository.save(real);

        google("mock:real@example.com:Hacker").andExpect(status().isConflict());
    }

    @Test
    void malformedMockTokenReturns401() throws Exception {
        for (String token : new String[]{"garbage", "mock:", "mock:not-an-email:Name", "eyJhbGciOiJSUzI1NiJ9.x.y"}) {
            google(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Google ID token không hợp lệ hoặc đã hết hạn")));
        }
        assertThat(userRepository.count()).isZero();
    }

    private ResultActions google(String idToken) throws Exception {
        return mvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("idToken", idToken))));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private void saveLocalUser(String email, Role role) {
        User user = new User();
        user.setFullName("Seed " + role);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        userRepository.save(user);
    }
}
