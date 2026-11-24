package com.prepdeck.user.controller;

import com.prepdeck.common.constant.AuthConstant;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.user.dto.request.LoginRequest;
import com.prepdeck.user.dto.request.RefreshTokenRequest;
import com.prepdeck.user.dto.request.RegisterRequest;
import com.prepdeck.user.dto.response.LoginResponse;
import com.prepdeck.user.dto.response.UserVO;
import com.prepdeck.user.service.AuthService;
import com.prepdeck.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authHeader) {
        Long userId = SecurityContext.getCurrentUserId();
        String token = authHeader.replace(AuthConstant.TOKEN_PREFIX, "");
        authService.logout(userId, token);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }
}
