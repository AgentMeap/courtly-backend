package com.se183891.badminton_backend.auth.controller;

import com.se183891.badminton_backend.auth.dto.AuthResponse;
import com.se183891.badminton_backend.auth.dto.ForgotPasswordRequest;
import com.se183891.badminton_backend.auth.dto.GoogleLoginRequest;
import com.se183891.badminton_backend.auth.dto.LoginRequest;
import com.se183891.badminton_backend.auth.dto.OtpSentResponse;
import com.se183891.badminton_backend.auth.dto.RefreshTokenRequest;
import com.se183891.badminton_backend.auth.dto.RegisterRequest;
import com.se183891.badminton_backend.auth.dto.ResetPasswordRequest;
import com.se183891.badminton_backend.auth.dto.SendOtpRequest;
import com.se183891.badminton_backend.auth.service.AuthService;
import com.se183891.badminton_backend.auth.service.GoogleAuthService;
import com.se183891.badminton_backend.common.response.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Dang ky, dang nhap, quen mat khau, refresh, dang xuat")
@SecurityRequirements // cac endpoint nay khong can Bearer token
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/register/send-otp")
    @Operation(summary = "Buoc 1 dang ky: gui ma OTP 6 so toi email")
    public OtpSentResponse sendRegisterOtp(@Valid @RequestBody SendOtpRequest request) {
        return authService.sendRegisterOtp(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buoc 2 dang ky: tao tai khoan LOCAL (role CUSTOMER) khi ma OTP dung")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Dang nhap bang email hoac so dien thoai")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Quen mat khau buoc 1: gui OTP neu email da dang ky",
            description = "Luon tra 200 voi cung mot body, ke ca khi email khong ton tai hoac bi gioi han tan suat "
                    + "(cooldown, so lan gui/gio): khi do chi im lang khong gui mail. Endpoint nay KHONG tra 429. "
                    + "503 neu he thong chua cau hinh SMTP.")
    public OtpSentResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Quen mat khau buoc 2: dat mat khau moi bang OTP, thu hoi moi phien dang nhap")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/google")
    @Operation(summary = "Dang nhap Google (Firebase ID token); 503 khi tinh nang dang tat")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return googleAuthService.loginWithGoogle(request.idToken());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Doi refresh token lay cap token moi (token cu bi thu hoi)")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Thu hoi refresh token (idempotent)")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }
}
