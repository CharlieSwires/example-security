package com.example.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DebugRequestFilter extends OncePerRequestFilter {

    private static final boolean ENABLED = false;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!ENABLED) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        if (uri.contains("/api/")) {
            HttpSession beforeSession = request.getSession(false);

            System.out.println();
            System.out.println("========== REQUEST START ==========");
            System.out.println("METHOD: " + request.getMethod());
            System.out.println("URI: " + request.getRequestURI());
            System.out.println("QUERY: " + request.getQueryString());
            System.out.println("ORIGIN: " + request.getHeader("Origin"));
            System.out.println("REFERER: " + request.getHeader("Referer"));
            System.out.println("CONTENT-TYPE: " + request.getHeader("Content-Type"));
            System.out.println("COOKIE HEADER: " + request.getHeader("Cookie"));
            System.out.println("X-XSRF-TOKEN HEADER PRESENT: " + (request.getHeader("X-XSRF-TOKEN") != null));
            System.out.println("SESSION BEFORE: " + (beforeSession == null ? "none" : beforeSession.getId()));
            System.out.println("COOKIES BEFORE: " + cookiesToString(request.getCookies()));

            Authentication beforeAuth = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("AUTH BEFORE: " + authToString(beforeAuth));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (uri.contains("/api/")) {
                HttpSession afterSession = request.getSession(false);
                Authentication afterAuth = SecurityContextHolder.getContext().getAuthentication();

                System.out.println("STATUS: " + response.getStatus());
                System.out.println("SESSION AFTER: " + (afterSession == null ? "none" : afterSession.getId()));
                System.out.println("AUTH AFTER: " + authToString(afterAuth));
                System.out.println("========== REQUEST END ==========");
                System.out.println();
            }
        }
    }

    private String cookiesToString(Cookie[] cookies) {
        if (cookies == null || cookies.length == 0) {
            return "none";
        }

        return Arrays.stream(cookies)
                .map(cookie -> cookie.getName() + "=" + mask(cookie.getValue()))
                .collect(Collectors.joining("; "));
    }

    private String authToString(Authentication authentication) {
        if (authentication == null) {
            return "none";
        }

        return "name=" + authentication.getName()
                + ", authenticated=" + authentication.isAuthenticated()
                + ", authorities=" + authentication.getAuthorities();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (value.length() <= 8) {
            return "***";
        }

        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}