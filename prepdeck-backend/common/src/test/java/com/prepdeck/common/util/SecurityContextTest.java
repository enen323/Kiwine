package com.prepdeck.common.util;

import com.prepdeck.common.constant.AuthConstant;
import com.prepdeck.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityContextTest {

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // 测：缺少 X-Gateway-Source 请求头 → 抛 UnauthorizedException（P1-5 修复）
    @Test
    void getCurrentUserId_throwsWhenMissingGatewaySource() {
        when(request.getHeader("X-Gateway-Source")).thenReturn(null);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                SecurityContext::getCurrentUserId);
        assertEquals("非法请求来源", ex.getMessage());
    }

    // 测：X-Gateway-Source 值不匹配 → 抛 UnauthorizedException
    @Test
    void getCurrentUserId_throwsWhenGatewaySourceMismatch() {
        when(request.getHeader("X-Gateway-Source")).thenReturn("evil-proxy");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                SecurityContext::getCurrentUserId);
        assertEquals("非法请求来源", ex.getMessage());
    }

    // 测：正确的 X-Gateway-Source + X-Gateway-Secret + X-User-Id → 返回用户 ID
    @Test
    void getCurrentUserId_returnsUserIdWhenSourceValid() {
        when(request.getHeader("X-Gateway-Source")).thenReturn("prepdeck-gateway");
        when(request.getHeader("X-Gateway-Secret")).thenReturn("secret");
        when(request.getHeader(AuthConstant.X_USER_ID)).thenReturn("42");

        Long userId = SecurityContext.getCurrentUserId();
        assertEquals(42L, userId);
    }

    // 测：正确的来源但缺少 X-User-Id → 抛 UnauthorizedException
    @Test
    void getCurrentUserId_throwsWhenMissingUserId() {
        when(request.getHeader("X-Gateway-Source")).thenReturn("prepdeck-gateway");
        when(request.getHeader("X-Gateway-Secret")).thenReturn("secret");
        when(request.getHeader(AuthConstant.X_USER_ID)).thenReturn(null);

        assertThrows(UnauthorizedException.class, SecurityContext::getCurrentUserId);
    }

    // 测：getCurrentUserRole 校验 X-Gateway-Source
    @Test
    void getCurrentUserRole_returnsNullWhenNoSource() {
        when(request.getHeader("X-Gateway-Source")).thenReturn(null);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                SecurityContext::getCurrentUserRole);
        assertEquals("非法请求来源", ex.getMessage());
    }

    // 测：正确的来源下 getCurrentUserRole 返回角色
    @Test
    void getCurrentUserRole_returnsRoleWhenSourceValid() {
        when(request.getHeader("X-Gateway-Source")).thenReturn("prepdeck-gateway");
        when(request.getHeader("X-Gateway-Secret")).thenReturn("secret");
        when(request.getHeader(AuthConstant.X_USER_ROLE)).thenReturn("USER");

        String role = SecurityContext.getCurrentUserRole();
        assertEquals("USER", role);
    }
}
