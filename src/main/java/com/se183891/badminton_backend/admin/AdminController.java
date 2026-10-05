package com.se183891.badminton_backend.admin;

import com.se183891.badminton_backend.common.response.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    @GetMapping("/ping")
    @Operation(summary = "Kiem tra quyen ADMIN")
    public MessageResponse ping() {
        return new MessageResponse("pong");
    }
}
