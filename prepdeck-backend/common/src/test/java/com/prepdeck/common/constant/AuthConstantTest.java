package com.prepdeck.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthConstantTest {

    @Test
    void tokenPrefix() {
        assertEquals("Bearer ", AuthConstant.TOKEN_PREFIX);
    }

    @Test
    void headerNames() {
        assertEquals("Authorization", AuthConstant.AUTHORIZATION_HEADER);
        assertEquals("X-User-Id", AuthConstant.X_USER_ID);
        assertEquals("X-User-Role", AuthConstant.X_USER_ROLE);
    }

    @Test
    void tokenExpiryValues() {
        assertEquals(2 * 60 * 60 * 1000L, AuthConstant.ACCESS_TOKEN_EXPIRE_MS);
        assertEquals(7 * 24 * 60 * 60 * 1000L, AuthConstant.REFRESH_TOKEN_EXPIRE_MS);
    }

    @Test
    void privateConstructorCanBeAccessed() throws Exception {
        var ctor = AuthConstant.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }
}
