package com.example.security.controller;

import com.example.security.dto.ForgotPasswordRequest;
import com.example.security.dto.ResetPasswordRequest;
import com.example.security.dto.UpdatePasswordRequest;
import com.example.security.security.SecurityAuditService;
import com.example.security.security.PasswordResetThrottleService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/password")
public class PasswordController {
    private final UserService userService;
    private final SecurityAuditService auditService;
    private final PasswordResetThrottleService passwordResetThrottleService;

    public PasswordController(UserService userService, SecurityAuditService auditService,
                              PasswordResetThrottleService passwordResetThrottleService) {
        this.userService = userService;
        this.auditService = auditService;
        this.passwordResetThrottleService = passwordResetThrottleService;
    }

    @PostMapping("/forgot")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String clientIp = auditService.clientIp(httpRequest);
        Optional<Duration> retryAfter = passwordResetThrottleService.retryAfter(request.email(), clientIp);
        if (retryAfter.isPresent()) {
            httpResponse.setHeader(HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, retryAfter.get().toSeconds())));
            auditService.record("PASSWORD_RESET_THROTTLED", null, null, false,
                    "request_limit_reached", httpRequest);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many password reset requests. Please wait and try again.");
        }
        passwordResetThrottleService.recordRequest(request.email(), clientIp);
        userService.sendForgotPasswordEmailIfVerifiedEmailExists(request.email());
        auditService.record("PASSWORD_RESET_REQUESTED", null, null, true, "generic_response", httpRequest);
        return Map.of("message", "If that email address is verified, a password reset link has been sent.");
    }

    @PostMapping("/validate")
    public Map<String, String> validatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        userService.validatePassword(request.password());
        return Map.of(
                "message", "Password is valid.",
                "rule", userService.passwordRuleMessage()
        );
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
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
