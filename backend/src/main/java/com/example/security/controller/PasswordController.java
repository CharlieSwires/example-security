package com.example.security.controller;

import com.example.security.dto.ForgotPasswordRequest;
import com.example.security.dto.ResetPasswordRequest;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/password")
public class PasswordController {
    private final UserService userService;
    private final SecurityAuditService auditService;

    public PasswordController(UserService userService, SecurityAuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @PostMapping("/forgot")
    public Map<String, String> forgotPassword(@RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        userService.sendForgotPasswordEmailIfVerifiedEmailExists(request.email());
        auditService.record("PASSWORD_RESET_REQUESTED", null, null, true, "generic_response", httpRequest);
        return Map.of("message", "If that email address is verified, a password reset link has been sent.");
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        boolean changed = userService.resetPasswordWithToken(request.token(), request.password());
        auditService.record("PASSWORD_RESET_COMPLETED", null, null, changed, changed ? "reset_success" : "invalid_or_expired_token", httpRequest);
        if (!changed) {
            return ResponseEntity.badRequest().body(Map.of("message", "The reset link is invalid or expired."));
        }
        return ResponseEntity.ok(Map.of("message", "Password changed."));
    }

    @PostMapping("/change-link")
    public Map<String, String> sendChangePasswordLink(Authentication authentication, HttpServletRequest httpRequest) {
        userService.sendPasswordChangeLinkForUsername(authentication.getName());
        auditService.record("PASSWORD_CHANGE_LINK_REQUESTED", authentication.getName(), authentication.getName(), true, "user_requested_change_link", httpRequest);
        return Map.of("message", "A password change link has been sent to your verified email address.");
    }
}
