package com.example.security.controller;

import com.example.security.dto.AuthRequest;
import com.example.security.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        System.out.println();
        System.out.println("========== LOGIN START ==========");
        System.out.println("LOGIN USERNAME: " + request.username());
        System.out.println("SESSION BEFORE LOGIN: " +
                (httpRequest.getSession(false) == null ? "none" : httpRequest.getSession(false).getId()));

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

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
}
