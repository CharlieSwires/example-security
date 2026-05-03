package com.example.security.controller;

import com.example.security.dto.AuthRequest;
import com.example.security.dto.AuthResponse;
import com.example.security.security.LoginAttemptService;
import com.example.security.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService auditService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginAttemptService loginAttemptService,
            SecurityAuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = auditService.clientIp(httpRequest);
        String username = request.username() == null ? "" : request.username().trim();

        Optional<Duration> existingRetryAfter = loginAttemptService.retryAfter(username, clientIp);
        if (existingRetryAfter.isPresent()) {
            applyRetryAfter(httpResponse, existingRetryAfter.get());
            auditService.record("LOGIN_THROTTLED", username, username, false, "pre_auth_lockout", httpRequest);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Please wait and try again.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password())
            );
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailedLogin(username, clientIp);
            Optional<Duration> retryAfter = loginAttemptService.retryAfter(username, clientIp);
            if (retryAfter.isPresent()) {
                applyRetryAfter(httpResponse, retryAfter.get());
                auditService.record("LOGIN_THROTTLED", username, username, false, "failure_threshold_reached", httpRequest);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed login attempts. Please wait and try again.");
            }
            auditService.record("LOGIN_FAILURE", username, username, false, "bad_credentials", httpRequest);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        loginAttemptService.recordSuccessfulLogin(username, clientIp);

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        auditService.record("LOGIN_SUCCESS", authentication.getName(), authentication.getName(), true, "authenticated", httpRequest,
                Map.of("session", session == null ? "none" : "created"));

        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new AuthResponse(authentication.getName(), roles);
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new AuthResponse(authentication.getName(), roles);
    }

    private void applyRetryAfter(HttpServletResponse response, Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    }
}
