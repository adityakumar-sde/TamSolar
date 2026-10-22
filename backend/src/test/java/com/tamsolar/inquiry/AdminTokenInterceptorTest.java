package com.tamsolar.inquiry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminTokenInterceptorTest {

    @Test
    void requiresConfiguredAdminTokenAndRejectsIncorrectValues() throws Exception {
        AdminTokenInterceptor interceptor = new AdminTokenInterceptor("expected-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.getStatus() == 401);

        request.addHeader("X-Admin-Token", "wrong-secret");
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));

        request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Token", "expected-secret");
        response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void failsClosedWhenNoTokenIsConfigured() throws Exception {
        AdminTokenInterceptor interceptor = new AdminTokenInterceptor("");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
        assertTrue(response.getStatus() == 503);
    }
}
