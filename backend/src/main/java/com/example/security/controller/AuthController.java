package com.example.security.controller;

import com.example.security.dto.AuthResponse;
import com.example.security.dto.AuthRequest;
import com.example.security.dto.LoginResponse;
import com.example.security.dto.MfaVerifyRequest;
import com.example.security.security.LoginAttemptService;
import com.example.security.security.LoginCompletionService;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.MfaService;
import com.example.security.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final LoginCompletionService loginCompletionService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService auditService;
    private final MfaService mfaService;
    private final UserDetailsService userDetailsService;
    private final UserService userService;

    public AuthController(
            AuthenticationManager authenticationManager,
            LoginCompletionService loginCompletionService,
            LoginAttemptService loginAttemptService,
            SecurityAuditService auditService,
            MfaService mfaService,
            UserDetailsService userDetailsService,
            UserService userService
    ) {
        this.authenticationManager = authenticationManager;
        this.loginCompletionService = loginCompletionService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.mfaService = mfaService;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody AuthRequest request,
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

        try {
            userService.upgradePasswordHashIfNeeded(authentication.getName(), request.password());
        } catch (RuntimeException ex) {
            // Authentication has already succeeded. Do not turn an opportunistic
            // legacy-hash upgrade failure into a failed login, and never log the password.
            log.error("Could not upgrade legacy password hash for authenticated user {}",
                    authentication.getName(), ex);
            auditService.record("PASSWORD_HASH_UPGRADE_FAILED", authentication.getName(),
                    authentication.getName(), false, "database_update_failed", httpRequest);
        }

        if (mfaService.isEnabled(authentication.getName())) {
            return loginCompletionService.beginMfaChallenge(authentication, httpRequest, httpResponse,
                    "password_verified");
        }

        loginAttemptService.recordSuccessfulLogin(username, clientIp);
        return loginCompletionService.complete(authentication, httpRequest, httpResponse, "password_only");
    }

    @PostMapping("/login/mfa")
    public LoginResponse verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) throw mfaChallengeExpired();

        Object usernameValue = session.getAttribute(LoginCompletionService.MFA_USERNAME);
        Object createdValue = session.getAttribute(LoginCompletionService.MFA_CREATED);
        Object attemptsValue = session.getAttribute(LoginCompletionService.MFA_ATTEMPTS);
        if (!(usernameValue instanceof String username) || !(createdValue instanceof Long created)) {
            clearMfaChallenge(session);
            throw mfaChallengeExpired();
        }

        if (java.time.Instant.now().getEpochSecond() - created > LoginCompletionService.MFA_CHALLENGE_SECONDS) {
            clearMfaChallenge(session);
            throw mfaChallengeExpired();
        }

        int attempts = attemptsValue instanceof Integer value ? value : 0;
        if (attempts >= LoginCompletionService.MFA_MAX_ATTEMPTS) {
            clearMfaChallenge(session);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many MFA attempts. Sign in again.");
        }

        if (!mfaService.verifyForLogin(username, request.code())) {
            attempts++;
            session.setAttribute(LoginCompletionService.MFA_ATTEMPTS, attempts);
            auditService.record("MFA_FAILURE", username, username, false, "invalid_code", httpRequest,
                    Map.of("attempt", Integer.toString(attempts)));
            if (attempts >= LoginCompletionService.MFA_MAX_ATTEMPTS) clearMfaChallenge(session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Authy or recovery code");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());

        clearMfaChallenge(session);
        loginAttemptService.recordSuccessfulLogin(username, auditService.clientIp(httpRequest));
        auditService.record("MFA_SUCCESS", username, username, true, "totp_or_recovery_verified", httpRequest);
        return loginCompletionService.complete(authentication, httpRequest, httpResponse, "mfa_verified");
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        String officeId = userService.findByUsername(authentication.getName())
                .map(user -> user.getOfficeId())
                .orElse(null);
        return new AuthResponse(authentication.getName(), roles, officeId);
    }

    private ResponseStatusException mfaChallengeExpired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA challenge expired. Sign in again.");
    }

    private void clearMfaChallenge(HttpSession session) {
        loginCompletionService.clearMfaChallenge(session);
    }

    private void applyRetryAfter(HttpServletResponse response, Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    }
}
