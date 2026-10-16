package com.se183891.badminton_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mat khau moi dung CUNG quy tac voi dang ky (xem {@link RegisterRequest}).
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        // Ma 6 so gui toi email qua POST /api/auth/password/forgot
        @NotBlank(message = "Vui lòng nhập mã OTP")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
        String otp,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)\\S(?:.*\\S)?$",
                message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ số và không có khoảng trắng ở đầu/cuối")
        String newPassword
) {
}
