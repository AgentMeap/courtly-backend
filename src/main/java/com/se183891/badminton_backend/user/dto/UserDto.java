package com.se183891.badminton_backend.user.dto;

import com.se183891.badminton_backend.user.entity.AuthProvider;
import com.se183891.badminton_backend.user.entity.Role;
import com.se183891.badminton_backend.user.entity.User;

import java.util.UUID;

public record UserDto(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        String avatarUrl,
        AuthProvider authProvider
) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getAvatarUrl(), user.getAuthProvider());
    }
}
