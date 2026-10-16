package com.se183891.badminton_backend.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Kiem tra cac bien moi truong bat buoc NGAY khi khoi dong (truoc khi tao DataSource),
 * de bao loi ro rang thay vi loi ket noi SQL Server kho hieu.
 * Dong thoi chan cau hinh Google mock nguy hiem (mock o prod, mock + Firebase).
 * Dang ky trong META-INF/spring.factories.
 */
public class RequiredSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Chi kiem tra khi file cau hinh cua app da duoc nap (bo qua context phu nhu bootstrap)
        if (!environment.containsProperty("app.jwt.issuer")) {
            return;
        }

        if (!StringUtils.hasText(environment.getProperty("spring.datasource.password"))) {
            throw new IllegalStateException("""

                    ==================================================================
                     Thieu bien moi truong DB_PASSWORD (mat khau SQL Server).
                     Vi du (PowerShell):  $env:DB_PASSWORD = "<mat khau sa>"
                     Tuy chon them: DB_URL, DB_USERNAME (mac dinh: sa)
                    ==================================================================
                    """);
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !StringUtils.hasText(environment.getProperty("JWT_SECRET"))) {
            throw new IllegalStateException(
                    "SPRING_PROFILES_ACTIVE=prod bat buoc phai dat bien moi truong JWT_SECRET (>= 32 byte)");
        }

        checkGoogleMock(environment);
    }

    /**
     * Google mock chi danh cho dev: cam bat o prod, va cam bat cung Firebase (khong ro dung bo nao).
     */
    private static void checkGoogleMock(ConfigurableEnvironment environment) {
        if (!environment.getProperty("app.google.mock-enabled", Boolean.class, false)) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "GOOGLE_MOCK_ENABLED=true (app.google.mock-enabled) KHONG duoc phep khi SPRING_PROFILES_ACTIVE=prod."
                            + " Mock Google login chi dung cho dev.");
        }
        if (environment.getProperty("firebase.enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Khong the bat dong thoi FIREBASE_ENABLED=true va GOOGLE_MOCK_ENABLED=true: hay tat mot trong hai.");
        }
    }

    @Override
    public int getOrder() {
        // Chay sau ConfigDataEnvironmentPostProcessor (da nap application.properties)
        return Ordered.LOWEST_PRECEDENCE;
    }
}
