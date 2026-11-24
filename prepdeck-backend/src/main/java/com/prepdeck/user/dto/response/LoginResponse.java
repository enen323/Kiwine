package com.prepdeck.user.dto.response;

/**
 * 登录 / 注册 / 刷新 Token 统一响应
 *
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param expiresIn    accessToken 有效期（秒）
 * @param user         用户信息
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserVO user
) {
}
