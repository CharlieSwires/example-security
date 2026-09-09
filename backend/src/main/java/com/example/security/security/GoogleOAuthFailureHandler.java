package com.example.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {
    private final SecurityAuditService auditService;
    private final String frontendBaseUrl;

    public GoogleOAuthFailureHandler(SecurityAuditService auditService,
                                     @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.auditService = auditService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        auditService.record("GOOGLE_LOGIN_FAILURE", null, null, false, "provider_rejected", request);
        String target = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .queryParam("google", "provider_rejected")
                .build().encode().toUriString();
        response.sendRedirect(target);
    }
}
