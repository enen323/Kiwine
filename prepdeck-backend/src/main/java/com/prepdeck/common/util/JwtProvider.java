package com.prepdeck.common.util;

import com.prepdeck.common.config.SpringEnvironmentHolder;
import com.prepdeck.common.constant.AuthConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 提供者 —— 签发与校验
 *
 * <p>使用 HMAC-SHA256 对称签名。密钥必须通过环境变量 JWT_SECRET 注入，启动时校验，不允许默认值。</p>
 */
public final class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    private static final String SECRET_KEY_ENV = "JWT_SECRET";

    private static volatile JwtProvider instance;
    private final SecretKey secretKey;

    public JwtProvider(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    private static String readSecret() {
        String secret = SpringEnvironmentHolder.get(SECRET_KEY_ENV, null);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable " + SECRET_KEY_ENV + " is not set. " +
                    "Application startup aborted to prevent insecure default key usage.");
        }
        return secret;
    }

    /**
     * 获取单例（从环境变量读取密钥，启动时强制校验）
     */
    public static JwtProvider getInstance() {
        if (instance == null) {
            synchronized (JwtProvider.class) {
                if (instance == null) {
                    instance = new JwtProvider(readSecret());
                }
            }
        }
        return instance;
    }

    /**
      * 测试用：直接设置单例实例（绕过环境变量读取）
      */
    public static void setInstanceForTest(JwtProvider testInstance) {
        instance = testInstance;
    }

    /**
     * 创建 Access Token
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return JWT token 字符串
     */
    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, AuthConstant.ACCESS_TOKEN_EXPIRE_MS);
    }

    /**
     * 创建 Refresh Token
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return JWT token 字符串
     */
    public String createRefreshToken(Long userId, String role) {
        return createToken(userId, role, AuthConstant.REFRESH_TOKEN_EXPIRE_MS);
    }

    private String createToken(Long userId, String role, long expireMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验 Token 并解析 Claims
     *
     * @param token JWT token
     * @return Claims
     * @throws ExpiredJwtException Token 已过期
     * @throws JwtException        Token 无效
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 提取 jti（JWT ID），用于黑名单
     */
    public String getJti(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 解析 Claims（不校验签名和过期，仅读取）
     */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
