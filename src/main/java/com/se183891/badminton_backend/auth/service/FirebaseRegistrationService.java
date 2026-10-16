package com.se183891.badminton_backend.auth.service;

import com.google.cloud.firestore.FieldValue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.se183891.badminton_backend.auth.dto.PhoneNumbers;
import com.se183891.badminton_backend.auth.dto.RegisterRequest;
import com.se183891.badminton_backend.auth.entity.OtpPurpose;
import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.common.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Dang ky luu o Firebase: sau khi OTP dung, BACKEND tao user trong Firebase Authentication
 * (Admin SDK) va ghi ho so vao Firestore users/{uid}. App Flutter khong tu tao tai khoan nua.
 * Chi hoat dong khi firebase.enabled=true; tat thi tra 503.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseRegistrationService {

    // Chi co bean khi firebase.enabled=true
    private final ObjectProvider<FirebaseApp> appProvider;
    private final ObjectProvider<FirebaseAuth> authProvider;
    private final OtpService otpService;

    /** Dung o buoc gui OTP: neu Firebase dang bat va email da co tai khoan thi bao 409 ngay, khong ton OTP. */
    public void ensureEmailAvailable(String email) {
        FirebaseAuth auth = authProvider.getIfAvailable();
        if (auth == null) {
            return;
        }
        try {
            auth.getUserByEmail(email);
        } catch (FirebaseAuthException ex) {
            if (ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return;
            }
            log.error("Khong kiem tra duoc email tren Firebase", ex);
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
        }
        throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
    }

    /**
     * OTP dung -> tao user Firebase + ho so Firestore. Loi o buoc nao thi rollback OTP
     * (nguoi dung dung lai duoc ma) va xoa user Firebase vua tao.
     */
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        FirebaseAuth auth = authProvider.getIfAvailable();
        FirebaseApp app = appProvider.getIfAvailable();
        if (auth == null || app == null) {
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_DISABLED);
        }
        String email = AuthService.normalizeEmail(request.email());
        String phone = PhoneNumbers.normalize(request.phone());
        String fullName = request.fullName().trim();

        otpService.consume(otpService.verify(email, OtpPurpose.REGISTER, request.otp()));

        UserRecord created;
        try {
            created = auth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(request.password())
                    .setDisplayName(fullName)
                    .setEmailVerified(true)); // email da duoc xac minh bang OTP
        } catch (FirebaseAuthException ex) {
            if (ex.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw ApiException.conflict(ErrorMessages.EMAIL_TAKEN);
            }
            log.error("Tao user Firebase that bai", ex);
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
        }

        try {
            Map<String, Object> profile = new HashMap<>();
            profile.put("fullName", fullName);
            profile.put("email", email);
            if (phone != null) {
                profile.put("phone", phone);
            }
            profile.put("role", "CUSTOMER");
            profile.put("authProvider", "LOCAL");
            profile.put("createdAt", FieldValue.serverTimestamp());
            FirestoreClient.getFirestore(app).collection("users").document(created.getUid())
                    .set(profile).get();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Ghi ho so Firestore that bai, xoa user Firebase {}", created.getUid(), ex);
            try {
                auth.deleteUser(created.getUid());
            } catch (FirebaseAuthException deleteEx) {
                log.error("Khong xoa duoc user Firebase {}", created.getUid(), deleteEx);
            }
            throw ApiException.serviceUnavailable(ErrorMessages.FIREBASE_UNAVAILABLE);
        }
        return new MessageResponse(ErrorMessages.REGISTER_SUCCESS);
    }
}
