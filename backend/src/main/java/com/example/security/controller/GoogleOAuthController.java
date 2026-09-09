package com.example.security.controller;

import com.example.security.dto.GoogleLinkStatusResponse;
import com.example.security.model.AppUser;
import com.example.security.security.GoogleOAuthSuccessHandler;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.GoogleAccountService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/google")
public class GoogleOAuthController {
    private final boolean available;
    private final UserService userService;
    private final GoogleAccountService googleAccounts;
    private final SecurityAuditService auditService;

    public GoogleOAuthController(@Value("${app.oauth.google.enabled:false}") boolean available,
                                 UserService userService,
                                 GoogleAccountService googleAccounts,
                                 SecurityAuditService auditService) {
        this.available = available;
        this.userService = userService;
        this.googleAccounts = googleAccounts;
        this.auditService = auditService;
    }

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of("available", available);
    }

    @GetMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        requireAvailable();
        clearLinkRequest(request.getSession(false));
        response.sendRedirect(request.getContextPath() + "/oauth2/authorization/google");
    }

    @GetMapping("/status")
    public GoogleLinkStatusResponse status(Authentication authentication) {
        AppUser user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new GoogleLinkStatusResponse(available, user.getGoogleSubject() != null,
                user.isEmailVerified() ? user.getEmail() : null, user.getGoogleLinkedAt());
    }

    @PostMapping("/link")
    public Map<String, String> link(Authentication authentication, HttpServletRequest request) {
        requireAvailable();
        AppUser user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEmailVerified() || user.getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Verify the application's email address before linking Google");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(GoogleOAuthSuccessHandler.LINK_USERNAME, authentication.getName());
        session.setAttribute(GoogleOAuthSuccessHandler.LINK_CREATED, Instant.now().getEpochSecond());
        return Map.of("authorizationPath", "/oauth2/authorization/google");
    }

    @DeleteMapping("/link")
    public void unlink(Authentication authentication, HttpServletRequest request,
                       HttpServletResponse response) {
        googleAccounts.unlink(authentication.getName());
        auditService.record("GOOGLE_UNLINK_SUCCESS", authentication.getName(), authentication.getName(), true,
                "authenticated_session", request);
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    private void requireAvailable() {
        if (!available) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Google login is not enabled");
    }

    private void clearLinkRequest(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(GoogleOAuthSuccessHandler.LINK_USERNAME);
        session.removeAttribute(GoogleOAuthSuccessHandler.LINK_CREATED);
    }
}
