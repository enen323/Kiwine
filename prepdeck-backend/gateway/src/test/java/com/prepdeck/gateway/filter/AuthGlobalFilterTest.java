package com.prepdeck.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthGlobalFilterTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            "prepdeck-default-dev-secret-key-min-32-chars!!".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        // 绕过环境变量校验，直接注入测试实例
        com.prepdeck.common.util.JwtProvider.setInstanceForTest(
                new com.prepdeck.common.util.JwtProvider("prepdeck-default-dev-secret-key-min-32-chars!!"));
    }

    @Test
    void getOrder() {
        AuthGlobalFilter filter = new AuthGlobalFilter();
        assertEquals(-200, filter.getOrder());
    }

    @Test
    void whitelistContainsExpectedPaths() throws Exception {
        Field whitelistField = AuthGlobalFilter.class.getDeclaredField("WHITELIST");
        whitelistField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> whitelist = (List<String>) whitelistField.get(null);

        assertTrue(whitelist.stream().anyMatch(p -> p.startsWith("/api/v1/auth/login")));
        assertTrue(whitelist.stream().anyMatch(p -> p.startsWith("/api/v1/auth/register")));
        assertTrue(whitelist.stream().anyMatch(p -> p.startsWith("/api/v1/auth/refresh")));
        assertTrue(whitelist.contains("/actuator/health"));
        assertTrue(whitelist.contains("/health"));
    }

    @Test
    void validateTokenValidJwt() throws Exception {
        String token = createTestToken(12345L, "user");

        Method validateMethod = AuthGlobalFilter.class.getDeclaredMethod("validateToken", String.class);
        validateMethod.setAccessible(true);

        AuthGlobalFilter filter = new AuthGlobalFilter();
        Claims claims = (Claims) validateMethod.invoke(filter, token);

        assertNotNull(claims);
        assertEquals("12345", claims.getSubject());
        assertEquals("user", claims.get("role"));
    }

    @Test
    void validateTokenInvalidJwtReturnsNull() throws Exception {
        Method validateMethod = AuthGlobalFilter.class.getDeclaredMethod("validateToken", String.class);
        validateMethod.setAccessible(true);

        AuthGlobalFilter filter = new AuthGlobalFilter();
        Object result = validateMethod.invoke(filter, "invalid.jwt.token");

        assertNull(result);
    }

    @Test
    void validateTokenNullTokenThrows() throws Exception {
        Method validateMethod = AuthGlobalFilter.class.getDeclaredMethod("validateToken", String.class);
        validateMethod.setAccessible(true);

        AuthGlobalFilter filter = new AuthGlobalFilter();
        // Passing null to parseSignedClaims will throw IllegalArgumentException
        try {
            validateMethod.invoke(filter, (Object) null);
            // If no exception thrown, fail
            fail("Expected exception when passing null token");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // The target exception is expected
            assertTrue(e.getCause() instanceof IllegalArgumentException
                    || e.getCause() instanceof io.jsonwebtoken.JwtException);
        }
    }

    @Test
    void extractTokenValidBearerToken() throws Exception {
        Method extractMethod = AuthGlobalFilter.class.getDeclaredMethod("extractToken",
                org.springframework.http.server.reactive.ServerHttpRequest.class);
        extractMethod.setAccessible(true);

        // Use a simple mock request builder
        org.springframework.mock.http.server.reactive.MockServerHttpRequest mockRequest =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("http://localhost/api/v1/users")
                        .header("Authorization", "Bearer test-token-123")
                        .build();

        AuthGlobalFilter filter = new AuthGlobalFilter();
        String token = (String) extractMethod.invoke(filter, mockRequest);

        assertEquals("test-token-123", token);
    }

    @Test
    void extractTokenMissingHeaderReturnsNull() throws Exception {
        Method extractMethod = AuthGlobalFilter.class.getDeclaredMethod("extractToken",
                org.springframework.http.server.reactive.ServerHttpRequest.class);
        extractMethod.setAccessible(true);

        org.springframework.mock.http.server.reactive.MockServerHttpRequest mockRequest =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("http://localhost/api/v1/users")
                        .build();

        AuthGlobalFilter filter = new AuthGlobalFilter();
        String token = (String) extractMethod.invoke(filter, mockRequest);

        assertNull(token);
    }

    @Test
    void extractTokenNonBearerHeaderReturnsNull() throws Exception {
        Method extractMethod = AuthGlobalFilter.class.getDeclaredMethod("extractToken",
                org.springframework.http.server.reactive.ServerHttpRequest.class);
        extractMethod.setAccessible(true);

        org.springframework.mock.http.server.reactive.MockServerHttpRequest mockRequest =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("http://localhost/api/v1/users")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build();

        AuthGlobalFilter filter = new AuthGlobalFilter();
        String token = (String) extractMethod.invoke(filter, mockRequest);

        assertNull(token);
    }

    private String createTestToken(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SECRET_KEY)
                .compact();
    }
}
