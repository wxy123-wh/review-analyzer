package com.wh.reputation.auth;

import com.wh.reputation.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request == null) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return true;
        }
        if (uri.startsWith("/api/auth/login")) {
            return true;
        }

        String authz = request.getHeader("Authorization");
        String token = extractBearerToken(authz);
        if (token == null || !authService.isValidToken(token)) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return true;
    }

    private static String extractBearerToken(String authz) {
        if (authz == null) {
            return null;
        }
        String trimmed = authz.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("bearer ")) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        return token.isBlank() ? null : token;
    }
}
