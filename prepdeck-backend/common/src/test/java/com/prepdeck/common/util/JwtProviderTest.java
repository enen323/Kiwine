package com.prepdeck.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtProviderTest {

    @AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field instanceField = JwtProvider.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // 测：readSecret 方法存在且可访问（P1-4 修复：密钥读取逻辑已提取为独立方法）
    @Test
    void readSecret_methodExists() throws Exception {
        Method readSecret = JwtProvider.class.getDeclaredMethod("readSecret");
        assertNotNull(readSecret);
        assertTrue(java.lang.reflect.Modifier.isPrivate(readSecret.getModifiers()));
    }

    // 测：setInstanceForTest 允许测试注入实例，避免依赖环境变量
    @Test
    void setInstanceForTest_injectsTestInstance() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider testProvider = (JwtProvider) ctor.newInstance(validSecret);
        JwtProvider.setInstanceForTest(testProvider);

        JwtProvider provider = JwtProvider.getInstance();
        assertSame(testProvider, provider);
    }

    // 测：使用 setInstanceForTest 后可正常创建 Access Token
    @Test
    void getInstance_createsAccessToken() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        String token = provider.createAccessToken(1L, "USER");
        assertNotNull(token);
        assertTrue(token.contains("."), "JWT should contain dots");
    }

    // 测：使用 setInstanceForTest 后可正常创建 Refresh Token
    @Test
    void getInstance_createsRefreshToken() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        String token = provider.createRefreshToken(1L, "USER");
        assertNotNull(token);
        assertTrue(token.contains("."));
    }

    // 测：getJti 从有效 token 中提取 jti（或返回 null 如果 JJWT 未自动生成 id）
    @Test
    void getJti_fromValidToken_returnsIdOrNull() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        String token = provider.createAccessToken(1L, "USER");
        String jti = provider.getJti(token);
        assertTrue(jti == null || !jti.isBlank(), "jti should be non-null+non-blank, or null");
    }

    // 测：getJti 对无效 token 返回 null
    @Test
    void getJti_returnsNullForInvalidToken() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        assertNull(provider.getJti("not.a.valid.token"));
    }

    // 测：validateToken 可正确校验有效 token
    @Test
    void validateToken_validatesCorrectToken() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        String token = provider.createAccessToken(1L, "USER");
        io.jsonwebtoken.Claims claims = provider.validateToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
    }

    // 测：构造函数拒绝短密钥（< 32 字符）
    @Test
    void constructor_rejectsShortSecret() {
        Exception ex = assertThrows(RuntimeException.class, () -> {
            java.lang.reflect.Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            try {
                ctor.newInstance("short");
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (RuntimeException) e.getCause();
            }
        });
        assertTrue(ex instanceof IllegalArgumentException,
                "Expected IllegalArgumentException but got: " + ex.getClass().getName());
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    // 测：getSecretKey 返回非 null SecretKey
    @Test
    void getSecretKey_returnsNonNullKey() throws Exception {
        String validSecret = "a-very-long-secret-key-that-is-at-least-32-chars!!";
        Constructor<?> ctor = JwtProvider.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        JwtProvider.setInstanceForTest((JwtProvider) ctor.newInstance(validSecret));

        JwtProvider provider = JwtProvider.getInstance();
        assertNotNull(provider.getSecretKey());
    }
}
