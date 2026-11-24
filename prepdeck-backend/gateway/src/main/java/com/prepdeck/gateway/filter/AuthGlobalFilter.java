package com.prepdeck.gateway.filter;

import com.prepdeck.common.util.JwtProvider;
import com.prepdeck.common.util.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 全局认证过滤器
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
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

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

    private static final String GATEWAY_SECRET = System.getenv().getOrDefault("GATEWAY_SECRET", "10b8e581a75345c78bdc195188a74e0f3dccf51ef33d4452b34c6bed67cd7422");

    // 构造器（Spring 注入）
    public AuthGlobalFilter(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    // 无参构造器（测试用）
    AuthGlobalFilter() {
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        if (token == null) {
            log.warn("网关认证失败: 缺少认证 Token, path={}", path);
            return unauthorized(exchange);
        }

        String jti = extractJti(token);
        if (jti != null) {
            String key = TokenBlacklist.blacklistKey(jti);
            Mono<Boolean> blacklisted = reactiveStringRedisTemplate.hasKey(key);
            Boolean blocked = blacklisted.block();
            if (Boolean.TRUE.equals(blocked)) {
                log.warn("网关认证失败: Token 已失效(黑名单), jti={}, path={}", jti, path);
                return unauthorized(exchange);
            }
        }

        Claims claims;
        try {
            claims = JwtProvider.getInstance().validateToken(token);
        } catch (JwtException e) {
            log.warn("JWT 签名校验失败: {}", e.getMessage());
            return unauthorized(exchange);
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Role", claims.get("role", String.class))
                .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", GATEWAY_SECRET)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String extractJti(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(JwtProvider.getInstance().getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getId();
        } catch (Exception e) {
            return null;
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    // 供测试使用的包级方法
    Claims validateToken(String token) {
        try {
            return JwtProvider.getInstance().validateToken(token);
        } catch (JwtException e) {
            return null;
        }
    }
}
