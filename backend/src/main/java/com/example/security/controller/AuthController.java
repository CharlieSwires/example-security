package com.example.security.controller;

import com.example.security.dto.AuthResponse;
import com.example.security.dto.AuthRequest;
import com.example.security.dto.LoginResponse;
import com.example.security.dto.MfaVerifyRequest;
import com.example.security.security.LoginAttemptService;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.MfaService;
import com.example.security.service.UserService;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final String MFA_USERNAME = "MFA_LOGIN_USERNAME";
    private static final String MFA_CREATED = "MFA_LOGIN_CREATED";
    private static final String MFA_ATTEMPTS = "MFA_LOGIN_ATTEMPTS";
    private static final long MFA_CHALLENGE_SECONDS = 5 * 60;
    private static final int MFA_MAX_ATTEMPTS = 5;

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService auditService;
    private final MfaService mfaService;
    private final UserDetailsService userDetailsService;
    private final UserService userService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginAttemptService loginAttemptService,
            SecurityAuditService auditService,
            MfaService mfaService,
            UserDetailsService userDetailsService,
            UserService userService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.mfaService = mfaService;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(
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

        userService.upgradePasswordHashIfNeeded(authentication.getName(), request.password());

        if (mfaService.isEnabled(authentication.getName())) {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(MFA_USERNAME, authentication.getName());
            session.setAttribute(MFA_CREATED, Instant.now().getEpochSecond());
            session.setAttribute(MFA_ATTEMPTS, 0);
            auditService.record("MFA_CHALLENGE", authentication.getName(), authentication.getName(), true,
                    "password_verified", httpRequest);
            return LoginResponse.mfaRequired(authentication.getName());
        }

        loginAttemptService.recordSuccessfulLogin(username, clientIp);
        return completeAuthentication(authentication, httpRequest, httpResponse, "password_only");
    }

    @PostMapping("/login/mfa")
    public LoginResponse verifyMfa(
            @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) throw mfaChallengeExpired();

        Object usernameValue = session.getAttribute(MFA_USERNAME);
        Object createdValue = session.getAttribute(MFA_CREATED);
        Object attemptsValue = session.getAttribute(MFA_ATTEMPTS);
        if (!(usernameValue instanceof String username) || !(createdValue instanceof Long created)) {
            clearMfaChallenge(session);
            throw mfaChallengeExpired();
        }

        if (Instant.now().getEpochSecond() - created > MFA_CHALLENGE_SECONDS) {
            clearMfaChallenge(session);
            throw mfaChallengeExpired();
        }

        int attempts = attemptsValue instanceof Integer value ? value : 0;
        if (attempts >= MFA_MAX_ATTEMPTS) {
            clearMfaChallenge(session);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many MFA attempts. Sign in again.");
        }

        if (!mfaService.verifyForLogin(username, request.code())) {
            attempts++;
            session.setAttribute(MFA_ATTEMPTS, attempts);
            auditService.record("MFA_FAILURE", username, username, false, "invalid_code", httpRequest,
                    Map.of("attempt", Integer.toString(attempts)));
            if (attempts >= MFA_MAX_ATTEMPTS) clearMfaChallenge(session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authy or recovery code");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());

        clearMfaChallenge(session);
        loginAttemptService.recordSuccessfulLogin(username, auditService.clientIp(httpRequest));
        auditService.record("MFA_SUCCESS", username, username, true, "totp_or_recovery_verified", httpRequest);
        return completeAuthentication(authentication, httpRequest, httpResponse, "mfa_verified");
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new AuthResponse(authentication.getName(), roles);
    }

    private LoginResponse completeAuthentication(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            String method
    ) {
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        auditService.record("LOGIN_SUCCESS", authentication.getName(), authentication.getName(), true, method, httpRequest,
                Map.of("session", session == null ? "none" : "created"));

        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return LoginResponse.authenticated(authentication.getName(), roles);
    }

    private ResponseStatusException mfaChallengeExpired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA challenge expired. Sign in again.");
    }

    private void clearMfaChallenge(HttpSession session) {
        session.removeAttribute(MFA_USERNAME);
        session.removeAttribute(MFA_CREATED);
        session.removeAttribute(MFA_ATTEMPTS);
    }

    private void applyRetryAfter(HttpServletResponse response, Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    }
}
