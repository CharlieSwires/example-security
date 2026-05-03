package com.example.security.controller;

import com.example.security.security.SecurityAuditService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final UserService userService;
    private final SecurityAuditService auditService;

    public EmailController(UserService userService, SecurityAuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    public String verifyEmail(@RequestParam String token, HttpServletRequest request) {
        boolean verified = userService.verifyEmailToken(token);
        auditService.record("EMAIL_VERIFIED", null, null, verified, verified ? "verified" : "invalid_or_expired_token", request);

        if (verified) {
            return """
                    <!doctype html>
                    <html>
                    <body style=\"font-family: Arial, sans-serif; padding: 2rem;\">
                      <h1>Email verified</h1>
                      <p>Your email address has been saved and verified.</p>
                      <p>You can now close this tab and return to ExampleSecurity.</p>
                    </body>
                    </html>
                    """;
        }

        return """
                <!doctype html>
                <html>
                <body style=\"font-family: Arial, sans-serif; padding: 2rem;\">
                  <h1>Email verification failed</h1>
                  <p>The link was invalid or expired.</p>
                </body>
                </html>
                """;
    }
}
