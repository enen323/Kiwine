package com.prepdeck.common.constant;

/**
 * 认证相关常量
 */
public final class AuthConstant {

    private AuthConstant() {
    }

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 请求头：Authorization */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** 内部传递用户信息的请求头 */
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_ROLE = "X-User-Role";

    /** 网关内部共享密钥请求头 */
    public static final String X_GATEWAY_SECRET = "X-Gateway-Secret";

    /** Token 有效期（毫秒） */
    public static final long ACCESS_TOKEN_EXPIRE_MS = 2 * 60 * 60 * 1000L;   // 2 小时
    public static final long REFRESH_TOKEN_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L; // 7 天
}
