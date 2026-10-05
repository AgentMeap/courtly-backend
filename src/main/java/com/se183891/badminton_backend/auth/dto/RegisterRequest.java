package com.se183891.badminton_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        // Tuy chon. Chap nhan 0xxxxxxxxx hoac +84xxxxxxxxx (cho phep dau cach/cham/gach)
        @Pattern(regexp = PhoneNumbers.INPUT_REGEX, message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)\\S(?:.*\\S)?$",
                message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ số và không có khoảng trắng ở đầu/cuối")
        String password,

        // Ma 6 so gui toi email qua POST /api/auth/register/send-otp
        @NotBlank(message = "Vui lòng nhập mã OTP")
        @Pattern(regexp = "^\\d{6}$", message = "Mã OTP gồm 6 chữ số")
        String otp
) {
}
