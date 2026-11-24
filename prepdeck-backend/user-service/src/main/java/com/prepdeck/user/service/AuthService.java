package com.prepdeck.user.service;

import com.prepdeck.user.dto.request.LoginRequest;
import com.prepdeck.user.dto.request.RefreshTokenRequest;
import com.prepdeck.user.dto.request.RegisterRequest;
import com.prepdeck.user.dto.response.LoginResponse;

/**
 * 认证服务接口
 *
 * <p>注册、登录、Token 刷新、登出。</p>
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 登录响应（含 token 对和用户信息）
     * @throws com.prepdeck.common.exception.DuplicateException 手机号已存在
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 token 对和用户信息）
     * @throws com.prepdeck.common.exception.NotFoundException   用户不存在
     * @throws com.prepdeck.common.exception.BadRequestException 密码错误
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新 Token
     *
     * @param request 刷新请求（含 refreshToken）
     * @return 新的 token 对（令牌轮转）
     * @throws com.prepdeck.common.exception.BadRequestException Token 无效或已过期
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * 用户登出
     *
     * @param userId       用户 ID
     * @param refreshToken 待失效的刷新令牌
     */
    void logout(Long userId, String refreshToken);
}
