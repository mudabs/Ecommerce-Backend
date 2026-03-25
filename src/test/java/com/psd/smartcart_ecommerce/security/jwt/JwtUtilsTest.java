package com.psd.smartcart_ecommerce.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtUtilsTest {

    @Test
    void getJwtFromRequestPrefersAuthorizationHeader() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "springBootEcom");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");

        assertEquals("header-token", jwtUtils.getJwtFromRequest(request));
    }

    @Test
    void getJwtFromRequestFallsBackToCookie() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "springBootEcom");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("springBootEcom", "cookie-token"));

        assertEquals("cookie-token", jwtUtils.getJwtFromRequest(request));
    }

    @Test
    void getJwtFromRequestReturnsNullWhenHeaderAndCookieAreMissing() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtCookie", "springBootEcom");

        assertNull(jwtUtils.getJwtFromRequest(new MockHttpServletRequest()));
    }
}

