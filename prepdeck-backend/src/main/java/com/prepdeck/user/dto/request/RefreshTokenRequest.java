package com.prepdeck.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求
 *
 * @param refreshToken 刷新令牌
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
