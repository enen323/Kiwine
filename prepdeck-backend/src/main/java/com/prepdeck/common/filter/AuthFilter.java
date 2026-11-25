package com.prepdeck.common.filter;

import com.prepdeck.common.config.SpringEnvironmentHolder;
import com.prepdeck.common.constant.AuthConstant;
import com.prepdeck.common.util.JwtProvider;
import com.prepdeck.common.util.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 全局认证过滤器（原 gateway AuthGlobalFilter 的 servlet 移植版）
 *
 * <p>职责：<br>
 * - 白名单路径放行（登录、注册）<br>
 * - 提取并校验 JWT Token（含黑名单检查）<br>
 * - 注入 X-User-Id / X-User-Role 到下游请求头<br>
 * - Token 无效返回 401
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final String GATEWAY_SOURCE_HEADER = "X-Gateway-Source";
    private static final String GATEWAY_SOURCE_VALUE = "prepdeck-gateway";
    private static String gatewaySecret() {
        return SpringEnvironmentHolder.get("GATEWAY_SECRET", "10b8e581a75345c78bdc195188a74e0f3dccf51ef33d4452b34c6bed67cd7422");
    }

    /** 白名单路径 — 无需认证 */
    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/system/login",
            "/api/v1/payment/callback/",
            "/actuator/health",
            "/health"
    );

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            log.warn("网关认证失败: 缺少认证 Token, path={}", path);
            unauthorized(response);
            return;
        }

        String jti = JwtProvider.getInstance().getJti(token);
        if (jti != null) {
            String key = TokenBlacklist.blacklistKey(jti);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                log.warn("网关认证失败: Token 已失效(黑名单), jti={}, path={}", jti, path);
                unauthorized(response);
                return;
            }
        }

        Claims claims;
        try {
            claims = JwtProvider.getInstance().validateToken(token);
        } catch (JwtException e) {
            log.warn("JWT 签名校验失败: {}", e.getMessage());
            unauthorized(response);
            return;
        }

        filterChain.doFilter(
                new GatewayHeaderRequestWrapper(request, claims.getSubject(), claims.get("role", String.class)),
                response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AuthConstant.AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(AuthConstant.TOKEN_PREFIX)) {
            return authHeader.substring(AuthConstant.TOKEN_PREFIX.length());
        }
        return null;
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    /**
     * 注入网关内部请求头的包装器
     *
     * <p>对 X-User-Id / X-User-Role / X-Gateway-Source / X-Gateway-Secret 四个头
     * 同时覆盖 getHeader 与 getHeaders 并采用替换语义，杜绝客户端伪造头绕过认证
     * （与网关 mutate().header() 的替换语义一致）。</p>
     */
    private static class GatewayHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> gatewayHeaders;

        private GatewayHeaderRequestWrapper(HttpServletRequest request, String userId, String userRole) {
            super(request);
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put(AuthConstant.X_USER_ID, userId);
            headers.put(AuthConstant.X_USER_ROLE, userRole);
            headers.put(GATEWAY_SOURCE_HEADER, GATEWAY_SOURCE_VALUE);
            headers.put(AuthConstant.X_GATEWAY_SECRET, gatewaySecret());
            this.gatewayHeaders = headers;
        }

        @Override
        public String getHeader(String name) {
            if (gatewayHeaders.containsKey(name)) {
                return gatewayHeaders.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (gatewayHeaders.containsKey(name)) {
                String value = gatewayHeaders.get(name);
                return value != null ? Collections.enumeration(List.of(value)) : Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}
