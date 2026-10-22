
package com.tamsolar.inquiry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private final String configuredToken;

    public AdminTokenInterceptor(
            @Value("${tamsolar.admin.token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        response.setHeader("Cache-Control", "no-store");

        String submittedToken = request.getHeader("X-Admin-Token");

        boolean authorized = !configuredToken.isBlank()
                && submittedToken != null
                && !submittedToken.isBlank()
                && MessageDigest.isEqual(
                        configuredToken.getBytes(StandardCharsets.UTF_8),
                        submittedToken.getBytes(StandardCharsets.UTF_8));

        if (authorized) {
            return true;
        }

        response.setStatus(
                configuredToken.isBlank()
                        ? HttpStatus.SERVICE_UNAVAILABLE.value()
                        : HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write(
                configuredToken.isBlank()
                        ? "{\"error\":\"Admin access is not configured.\"}"
                        : "{\"error\":\"Admin authorization is required.\"}");

        return false;
    }
}
