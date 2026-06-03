package com.wh.reputation.auth;

import com.wh.reputation.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {
    private static final Map<String, UserDef> USERS = Map.of(
            "pm", new UserDef("123456", "PM"),
            "market", new UserDef("123456", "MARKET"),
            "ops", new UserDef("123456", "OPS"));

    private final JwtUtils jwtUtils;

    public AuthService(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    public LoginResponseDto login(String usernameRaw, String passwordRaw) {
        String username = usernameRaw == null ? "" : usernameRaw.trim().toLowerCase(Locale.ROOT);
        String password = passwordRaw == null ? "" : passwordRaw;
        UserDef def = USERS.get(username);
        if (def == null || !def.password().equals(password)) {
            throw new BadRequestException("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(username, def.role());
        return new LoginResponseDto(token, def.role());
    }

    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return jwtUtils.validateToken(token);
    }

    public String roleOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtUtils.getRole(token);
        } catch (Exception e) {
            return null;
        }
    }

    private record UserDef(String password, String role) {
    }
}
