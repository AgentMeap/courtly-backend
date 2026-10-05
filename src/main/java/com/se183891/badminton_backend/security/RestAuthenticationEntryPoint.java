package com.se183891.badminton_backend.security;

import com.se183891.badminton_backend.common.exception.ErrorMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Tra 401 dang JSON khi chua dang nhap / token sai / token het han.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message;
        if (JwtAuthenticationFilter.AUTH_ERROR_EXPIRED.equals(reason)) {
            message = ErrorMessages.TOKEN_EXPIRED;
        } else if (JwtAuthenticationFilter.AUTH_ERROR_INVALID.equals(reason)) {
            message = ErrorMessages.TOKEN_INVALID;
        } else {
            message = ErrorMessages.UNAUTHENTICATED;
        }
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, message);
    }
}
