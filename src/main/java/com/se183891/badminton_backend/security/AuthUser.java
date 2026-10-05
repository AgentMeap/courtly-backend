package com.se183891.badminton_backend.security;

import com.se183891.badminton_backend.user.entity.Role;

import java.util.UUID;

/**
 * Principal dat vao SecurityContext sau khi xac thuc access token thanh cong.
 */
public record AuthUser(UUID id, String email, Role role) {
}
