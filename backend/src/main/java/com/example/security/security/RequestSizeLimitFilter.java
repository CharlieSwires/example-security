package com.example.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Defence in depth when the backend is accidentally reached without Nginx. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private final long maximumBytes;

    public RequestSizeLimitFilter(@Value("${app.security.max-request-bytes:262144}") long maximumBytes) {
        if (maximumBytes < 1024) {
            throw new IllegalArgumentException("app.security.max-request-bytes must be at least 1024");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maximumBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Request body is too large\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
