package com.example.security.controller;

import com.example.security.dto.AuthRequest;
import com.example.security.dto.AuthResponse;
import com.example.security.security.LoginAttemptService;
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

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginAttemptService loginAttemptService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = clientIp(httpRequest);
        String username = request.username() == null ? "" : request.username().trim();

        Optional<Duration> existingRetryAfter = loginAttemptService.retryAfter(username, clientIp);
        if (existingRetryAfter.isPresent()) {
            applyRetryAfter(httpResponse, existingRetryAfter.get());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Please wait and try again.");
        }

        System.out.println();
        System.out.println("========== LOGIN START ==========");
        System.out.println("LOGIN USERNAME: " + username);
        System.out.println("LOGIN CLIENT IP: " + clientIp);
        System.out.println("SESSION BEFORE LOGIN: " +
                (httpRequest.getSession(false) == null ? "none" : httpRequest.getSession(false).getId()));

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
                System.out.println("LOGIN THROTTLED AFTER FAILURE for user=" + username + ", ip=" + clientIp);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed login attempts. Please wait and try again.");
            }
            System.out.println("AUTHENTICATION FAILED for user=" + username + ", ip=" + clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        loginAttemptService.recordSuccessfulLogin(username, clientIp);

        System.out.println("AUTHENTICATION SUCCESS: " + authentication.getName());
        System.out.println("AUTHENTICATION AUTHORITIES: " + authentication.getAuthorities());

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        System.out.println("SESSION CREATED/FETCHED: " + session.getId());

        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        System.out.println("SECURITY CONTEXT SAVED");
        System.out.println("SESSION AFTER LOGIN: " +
                (httpRequest.getSession(false) == null ? "none" : httpRequest.getSession(false).getId()));
        System.out.println("========== LOGIN END ==========");
        System.out.println();

        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new AuthResponse(authentication.getName(), roles);
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication, HttpServletRequest request) {
        System.out.println();
        System.out.println("========== /api/me ==========");
        System.out.println("SESSION: " +
                (request.getSession(false) == null ? "none" : request.getSession(false).getId()));
        System.out.println("AUTH: " + authentication);
        System.out.println("AUTHORITIES: " + authentication.getAuthorities());
        System.out.println("=============================");
        System.out.println();

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

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
