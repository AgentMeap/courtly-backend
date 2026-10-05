package com.se183891.badminton_backend.user.controller;

import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.common.exception.ErrorMessages;
import com.se183891.badminton_backend.security.AuthUser;
import com.se183891.badminton_backend.user.dto.UserDto;
import com.se183891.badminton_backend.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Thong tin user dang dang nhap")
    public UserDto me(@AuthenticationPrincipal AuthUser principal) {
        return userRepository.findById(principal.id())
                .filter(user -> user.isEnabled())
                .map(UserDto::from)
                // User da bi xoa/khoa sau khi cap token
                .orElseThrow(() -> ApiException.unauthorized(ErrorMessages.UNAUTHENTICATED));
    }
}
