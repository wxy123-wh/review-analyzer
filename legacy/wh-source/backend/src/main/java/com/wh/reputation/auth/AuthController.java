package com.wh.reputation.auth;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证模块", description = "用户登录认证接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "使用用户名/密码登录，返回JWT Token")
    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@RequestBody LoginRequest body) {
        if (body == null) {
            throw new BadRequestException("请求体不能为空");
        }
        return ApiResponse.ok(authService.login(body.username(), body.password()));
    }
}
