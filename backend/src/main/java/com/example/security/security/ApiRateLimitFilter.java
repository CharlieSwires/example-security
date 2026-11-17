/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-application-node token-bucket protection for API traffic.
 *
 * <p>The servlet request address has already been normalised by Tomcat's
 * RemoteIpValve. The valve only accepts forwarding headers from proxies that
 * match {@code TRUSTED_PROXY_REGEX}; this filter must not parse an untrusted
 * X-Forwarded-For header itself.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private static final long STALE_BUCKET_NANOS = Duration.ofMinutes(15).toNanos();

    private final boolean enabled;
    private final double requestsPerSecond;
    private final int burstCapacity;
    private final Set<String> allowlistedIps;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public ApiRateLimitFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.requests-per-second:15}") double requestsPerSecond,
            @Value("${app.security.rate-limit.burst-capacity:30}") int burstCapacity,
            @Value("${app.security.rate-limit.allowlisted-ips:}") String allowlistedIps
    ) {
        if (requestsPerSecond <= 0) throw new IllegalArgumentException("Rate limit must be greater than zero");
        if (burstCapacity < 1) throw new IllegalArgumentException("Rate limit burst capacity must be at least one");
        this.enabled = enabled;
        this.requestsPerSecond = requestsPerSecond;
        this.burstCapacity = burstCapacity;
        this.allowlistedIps = Arrays.stream(allowlistedIps.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !enabled
                || path == null
                || !path.startsWith("/api/")
                || allowlistedIps.contains(clientIp(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = clientIp(request);
        long now = System.nanoTime();
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                ignored -> new TokenBucket(burstCapacity, now));

        if (!bucket.tryConsume(now, requestsPerSecond, burstCapacity)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setHeader("X-RateLimit-Limit", formatLimit(requestsPerSecond));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            byte[] body = "{\"error\":\"Too many requests\"}".getBytes(StandardCharsets.UTF_8);
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            log.warn("API_RATE_LIMITED clientIp={} method={} path={}",
                    clientIp, request.getMethod(), request.getRequestURI());
            cleanStaleBucketsOccasionally(now);
            return;
        }

        response.setHeader("X-RateLimit-Limit", formatLimit(requestsPerSecond));
        cleanStaleBucketsOccasionally(now);
        filterChain.doFilter(request, response);
    }

    private void cleanStaleBucketsOccasionally(long now) {
        if ((requestCounter.incrementAndGet() & 1023) != 0) return;
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeenNanos() > STALE_BUCKET_NANOS);
    }

    private String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address.trim();
    }

    private String formatLimit(double limit) {
        return limit == Math.rint(limit) ? Long.toString((long) limit) : Double.toString(limit);
    }

    private static final class TokenBucket {
        private double tokens;
        private long lastRefillNanos;
        private long lastSeenNanos;

        private TokenBucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillNanos = now;
            this.lastSeenNanos = now;
        }

        private synchronized boolean tryConsume(long now, double rate, int capacity) {
            double elapsedSeconds = Math.max(0, now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSeconds * rate);
            lastRefillNanos = now;
            lastSeenNanos = now;
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }

        private synchronized long lastSeenNanos() {
            return lastSeenNanos;
        }
    }
}
