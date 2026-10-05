package com.se183891.badminton_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Doc header {@code Authorization: Bearer <jwt>} va dat AuthUser vao SecurityContext.
 * Token sai/het han KHONG chan request o day: endpoint public van chay binh thuong,
 * con endpoint can dang nhap se bi RestAuthenticationEntryPoint tra 401 kem ly do.
 * <p>
 * Co y KHONG danh dau @Component de Spring Boot khong tu dang ky them mot lan nua
 * nhu servlet filter thuong (chi chay trong SecurityFilterChain).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute de entry point biet vi sao xac thuc that bai. */
    public static final String AUTH_ERROR_ATTRIBUTE = "courtly.authError";
    public static final String AUTH_ERROR_EXPIRED = "expired";
    public static final String AUTH_ERROR_INVALID = "invalid";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                AuthUser user = jwtService.parseAccessToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtService.TokenValidationException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex.isExpired() ? AUTH_ERROR_EXPIRED : AUTH_ERROR_INVALID);
            }
        }
        filterChain.doFilter(request, response);
    }
}
