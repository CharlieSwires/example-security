package com.example.security.security;

import com.example.security.dto.LoginResponse;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoginCompletionService {
    public static final String MFA_USERNAME = "MFA_LOGIN_USERNAME";
    public static final String MFA_CREATED = "MFA_LOGIN_CREATED";
    public static final String MFA_ATTEMPTS = "MFA_LOGIN_ATTEMPTS";
    public static final long MFA_CHALLENGE_SECONDS = 5 * 60;
    public static final int MFA_MAX_ATTEMPTS = 5;

    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityAuditService auditService;
    private final UserService userService;

    public LoginCompletionService(SecurityContextRepository securityContextRepository,
                                  SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                  SecurityAuditService auditService,
                                  UserService userService) {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.auditService = auditService;
        this.userService = userService;
    }

    public LoginResponse beginMfaChallenge(Authentication authentication, HttpServletRequest request,
                                           HttpServletResponse response, String method) {
        clearAuthentication(request, response);
        HttpSession session = request.getSession(true);
        session.setAttribute(MFA_USERNAME, authentication.getName());
        session.setAttribute(MFA_CREATED, Instant.now().getEpochSecond());
        session.setAttribute(MFA_ATTEMPTS, 0);
        auditService.record("MFA_CHALLENGE", authentication.getName(), authentication.getName(), true,
                method, request);
        return LoginResponse.mfaRequired(authentication.getName());
    }

    public LoginResponse complete(Authentication authentication, HttpServletRequest request,
                                  HttpServletResponse response, String method) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        HttpSession session = request.getSession(true);
        clearMfaChallenge(session);
        auditService.record("LOGIN_SUCCESS", authentication.getName(), authentication.getName(), true, method, request,
                Map.of("session", session == null ? "none" : "created"));

        Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());
        String officeId = userService.findByUsername(authentication.getName())
                .map(user -> user.getOfficeId())
                .orElse(null);
        return LoginResponse.authenticated(authentication.getName(), roles, officeId);
    }

    public void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(empty, request, response);
    }

    public void clearMfaChallenge(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(MFA_USERNAME);
        session.removeAttribute(MFA_CREATED);
        session.removeAttribute(MFA_ATTEMPTS);
    }
}
