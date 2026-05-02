package com.example.security.controller;

import com.example.security.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final UserService userService;

    public EmailController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    public String verifyEmail(@RequestParam String token) {
        boolean verified = userService.verifyEmailToken(token);

        if (verified) {
            return """
                    <!doctype html>
                    <html>
                    <body style="font-family: Arial, sans-serif; padding: 2rem;">
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
                <body style="font-family: Arial, sans-serif; padding: 2rem;">
                  <h1>Email verification failed</h1>
                  <p>The link was invalid or expired.</p>
                </body>
                </html>
                """;
    }
}
