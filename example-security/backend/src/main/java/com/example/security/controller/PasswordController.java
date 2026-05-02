package com.example.security.controller;

import com.example.security.dto.ForgotPasswordRequest;
import com.example.security.dto.ResetPasswordRequest;
import com.example.security.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/password")
public class PasswordController {
    private final UserService userService;

    public PasswordController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/forgot")
    public Map<String, String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.sendForgotPasswordEmailIfVerifiedEmailExists(request.email());
        return Map.of("message", "If that email address is verified, a password reset link has been sent.");
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        boolean changed = userService.resetPasswordWithToken(request.token(), request.password());
        if (!changed) {
            return ResponseEntity.badRequest().body(Map.of("message", "The reset link is invalid or expired."));
        }
        return ResponseEntity.ok(Map.of("message", "Password changed."));
    }

    @PostMapping("/change-link")
    public Map<String, String> sendChangePasswordLink(Authentication authentication) {
        userService.sendPasswordChangeLinkForUsername(authentication.getName());
        return Map.of("message", "A password change link has been sent to your verified email address.");
    }
}
