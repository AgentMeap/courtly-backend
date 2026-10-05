package com.se183891.badminton_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Chi khoi tao FirebaseApp khi {@code firebase.enabled=true}.
 * Khi tat, khong co bean FirebaseAuth va /api/auth/google tra 503.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        // TODO(FIREBASE): dat FIREBASE_SERVICE_ACCOUNT_PATH tro toi file JSON service account
        //  (tai tu Firebase Console > Project settings > Service accounts). KHONG commit file nay.
        if (!StringUtils.hasText(properties.serviceAccountPath())) {
            throw new IllegalStateException(
                    "firebase.enabled=true nhung chua dat FIREBASE_SERVICE_ACCOUNT_PATH");
        }
        Path path = Path.of(properties.serviceAccountPath());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Khong doc duoc file service account Firebase: " + path);
        }

        FirebaseOptions.Builder options;
        try (InputStream in = Files.newInputStream(path)) {
            options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in));
        }
        // TODO(FIREBASE): dat FIREBASE_PROJECT_ID (vd: courtly-xxxxx), trung voi project cua app Flutter
        if (StringUtils.hasText(properties.projectId())) {
            options.setProjectId(properties.projectId());
        }

        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options.build())
                : FirebaseApp.getInstance();
        log.info("Firebase Admin SDK da khoi tao cho project {}", app.getOptions().getProjectId());
        return app;
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
