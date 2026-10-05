package com.se183891.badminton_backend.config;

import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;
import com.se183891.badminton_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tao tai khoan mau khi chay dev (bo qua neu email da ton tai).
 * Tat bang SEED_ENABLED=false; khong bao gio chay khi SPRING_PROFILES_ACTIVE=prod.
 * Mat khau mac dinh lay tu SEED_PASSWORD (mac dinh 123456).
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("Quản trị viên Courtly", "admin@courtly.vn", "0900000001", Role.ADMIN);
        seed("Nguyễn Văn Khách", "customer@courtly.vn", "0900000002", Role.CUSTOMER);
    }

    private void seed(String fullName, String email, String phone, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(userRepository.existsByPhone(phone) ? null : phone);
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Seeded {} account: {}", role, email);
    }
}
