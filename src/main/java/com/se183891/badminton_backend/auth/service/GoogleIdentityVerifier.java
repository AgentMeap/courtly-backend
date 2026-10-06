package com.se183891.badminton_backend.auth.service;

/**
 * Xac minh token dang nhap Google ma app gui len va tra ve danh tinh da xac minh.
 * Co toi da MOT ban duoc bat: {@link FirebaseGoogleIdentityVerifier} ({@code firebase.enabled=true})
 * hoac {@link MockGoogleIdentityVerifier} ({@code app.google.mock-enabled=true}, chi dev).
 * Khong co ban nao -> /api/auth/google tra 503.
 */
public interface GoogleIdentityVerifier {

    /**
     * @throws com.se183891.badminton_backend.common.exception.ApiException 401 khi token khong hop le
     */
    GoogleIdentity verify(String idToken);

    /** true voi bo kiem tra gia: danh tinh mock khong duoc cham toi tai khoan that. */
    default boolean isMock() {
        return false;
    }

    /** Danh tinh da xac minh. Email luon co gia tri va da duoc xac minh; name, picture co the null. */
    record GoogleIdentity(String uid, String email, String name, String picture) {
    }
}
