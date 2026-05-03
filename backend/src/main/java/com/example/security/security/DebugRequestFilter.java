package com.example.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Very noisy request tracing for local troubleshooting only.
 *
 * Disabled unless app.security.debug-request-logging=true.
 *
 * Do not enable this in production. It avoids printing cookie values, session IDs,
 * CSRF tokens, passwords, reset tokens, verification tokens or request bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.security", name = "debug-request-logging", havingValue = "true")
public class DebugRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (uri.contains("/api/")) {
            HttpSession beforeSession = request.getSession(false);
            Authentication beforeAuth = SecurityContextHolder.getContext().getAuthentication();

            System.out.println();
            System.out.println("========== REQUEST START ==========");
            System.out.println("METHOD: " + request.getMethod());
            System.out.println("URI: " + request.getRequestURI());
            System.out.println("ORIGIN: " + request.getHeader("Origin"));
            System.out.println("REFERER PRESENT: " + (request.getHeader("Referer") != null));
            System.out.println("CONTENT-TYPE: " + request.getHeader("Content-Type"));
            System.out.println("COOKIE HEADER PRESENT: " + (request.getHeader("Cookie") != null));
            System.out.println("X-XSRF-TOKEN HEADER PRESENT: " + (request.getHeader("X-XSRF-TOKEN") != null));
            System.out.println("SESSION BEFORE PRESENT: " + (beforeSession != null));
            System.out.println("AUTH BEFORE: " + authToString(beforeAuth));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (uri.contains("/api/")) {
                HttpSession afterSession = request.getSession(false);
                Authentication afterAuth = SecurityContextHolder.getContext().getAuthentication();

                System.out.println("STATUS: " + response.getStatus());
                System.out.println("SESSION AFTER PRESENT: " + (afterSession != null));
                System.out.println("AUTH AFTER: " + authToString(afterAuth));
                System.out.println("========== REQUEST END ==========");
                System.out.println();
            }
        }
    }

    private String authToString(Authentication authentication) {
        if (authentication == null) {
            return "none";
        }

        return "name=" + authentication.getName()
                + ", authenticated=" + authentication.isAuthenticated()
                + ", authorities=" + authentication.getAuthorities();
    }
}
