package com.example.security.security;

import com.example.security.model.LoginAttempt;
import com.example.security.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/** MongoDB-backed abuse protection for the public forgot-password endpoint. */
@Service
public class PasswordResetThrottleService {
    private final LoginAttemptRepository repository;
    private final int maxAccountRequests;
    private final int maxIpRequests;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;

    @Autowired
    public PasswordResetThrottleService(
            LoginAttemptRepository repository,
            @Value("${app.security.password-reset.max-account-requests:3}") int maxAccountRequests,
            @Value("${app.security.password-reset.max-ip-requests:20}") int maxIpRequests,
            @Value("${app.security.password-reset.window-minutes:60}") long windowMinutes,
            @Value("${app.security.password-reset.lockout-minutes:60}") long lockoutMinutes
    ) {
        this(repository, maxAccountRequests, maxIpRequests, Duration.ofMinutes(windowMinutes),
                Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    PasswordResetThrottleService(LoginAttemptRepository repository, int maxAccountRequests,
                                 int maxIpRequests, Duration window, Duration lockout, Clock clock) {
        this.repository = repository;
        this.maxAccountRequests = positive(maxAccountRequests, "max-account-requests");
        this.maxIpRequests = positive(maxIpRequests, "max-ip-requests");
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    public Optional<Duration> retryAfter(String email, String clientIp) {
        Instant now = Instant.now(clock);
        Optional<Duration> account = retryAfter(accountKey(email), now);
        Optional<Duration> ip = retryAfter(ipKey(clientIp), now);
        if (account.isEmpty()) return ip;
        if (ip.isEmpty()) return account;
        return Optional.of(account.get().compareTo(ip.get()) >= 0 ? account.get() : ip.get());
    }

    public void recordRequest(String email, String clientIp) {
        Instant now = Instant.now(clock);
        increment(accountKey(email), maxAccountRequests, now);
        increment(ipKey(clientIp), maxIpRequests, now);
    }

    private Optional<Duration> retryAfter(String key, Instant now) {
        LoginAttempt attempt = repository.findById(key).orElse(null);
        if (attempt == null) return Optional.empty();
        if (attempt.getFirstFailureAt() == null || attempt.getFirstFailureAt().plus(window).isBefore(now)) {
            repository.deleteById(key);
            return Optional.empty();
        }
        return attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now)
                ? Optional.of(Duration.between(now, attempt.getLockedUntil()))
                : Optional.empty();
    }

    private void increment(String key, int maximum, Instant now) {
        LoginAttempt existing = repository.findById(key).orElse(null);
        LoginAttempt next = new LoginAttempt();
        next.setKey(key);
        if (existing == null || existing.getFirstFailureAt() == null
                || existing.getFirstFailureAt().plus(window).isBefore(now)) {
            next.setFailedAttempts(1);
            next.setFirstFailureAt(now);
            if (maximum == 1) next.setLockedUntil(now.plus(lockout));
        } else {
            int count = existing.getFailedAttempts() + 1;
            next.setFailedAttempts(count);
            next.setFirstFailureAt(existing.getFirstFailureAt());
            next.setLockedUntil(count >= maximum ? now.plus(lockout) : existing.getLockedUntil());
        }
        Instant expiryBase = next.getLockedUntil() == null
                ? next.getFirstFailureAt().plus(window)
                : next.getLockedUntil();
        next.setExpiresAt(expiryBase.plus(Duration.ofMinutes(5)));
        repository.save(next);
    }

    private String accountKey(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "password-reset-account:" + sha256(normalized);
    }

    private String ipKey(String clientIp) {
        String normalized = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return "password-reset-ip:" + normalized;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create password-reset throttle key", ex);
        }
    }

    private int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
