package com.se183891.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * identifier: email HOAC so dien thoai.
 * Mat khau chi can >= 6 ky tu (de tai khoan seed "123456" van dang nhap duoc).
 */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại")
        @Size(max = 255, message = "Email/số điện thoại quá dài")
        String identifier,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
        String password
) {
}
