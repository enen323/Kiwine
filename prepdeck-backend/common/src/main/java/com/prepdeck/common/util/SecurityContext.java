package com.prepdeck.common.util;

import com.prepdeck.common.constant.AuthConstant;
import com.prepdeck.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全上下文 —— 获取当前请求中的用户信息
 *
 * <p>网关层已在请求头注入 X-User-Id / X-User-Role，各微服务通过此工具类获取。</p>
 *
 * <p>来源校验：只接受来自网关的请求，通过检查 X-Gateway-Source 请求头实现。</p>
 */
public final class SecurityContext {

    private SecurityContext() {
    }

    private static final String GATEWAY_SOURCE_HEADER = "X-Gateway-Source";
    private static final String GATEWAY_SOURCE_VALUE = "prepdeck-gateway";
    private static final String GATEWAY_SECRET_HEADER = AuthConstant.X_GATEWAY_SECRET;
    private static final String GATEWAY_SECRET = System.getenv().getOrDefault("GATEWAY_SECRET", "10b8e581a75345c78bdc195188a74e0f3dccf51ef33d4452b34c6bed67cd7422");

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID
     * @throws UnauthorizedException 未登录或请求头缺失或来源不合法
     */
    public static Long getCurrentUserId() {
        HttpServletRequest request = getRequest();
        validateGatewaySource(request);
        String userId = request.getHeader(AuthConstant.X_USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("未登录");
        }
        return Long.valueOf(userId);
    }

    /**
     * 获取当前用户角色
     *
     * @return 角色字符串
     */
    public static String getCurrentUserRole() {
        HttpServletRequest request = getRequest();
        validateGatewaySource(request);
        return request.getHeader(AuthConstant.X_USER_ROLE);
    }

    /**
     * 校验请求来源为网关，防止伪造 X-User-Id / X-User-Role
     */
    private static void validateGatewaySource(HttpServletRequest request) {
        String source = request.getHeader(GATEWAY_SOURCE_HEADER);
        if (!GATEWAY_SOURCE_VALUE.equals(source)) {
            throw new UnauthorizedException("非法请求来源");
        }
        String secret = request.getHeader(GATEWAY_SECRET_HEADER);
        if (secret == null || secret.isBlank()) {
            throw new UnauthorizedException("非法请求来源");
        }
        if (!GATEWAY_SECRET.equals(secret)) {
            throw new UnauthorizedException("非法请求来源");
        }
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new UnauthorizedException("未登录");
        }
        return attrs.getRequest();
    }
}
