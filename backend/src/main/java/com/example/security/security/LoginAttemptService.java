package com.example.security.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxUserIpFailures;
    private final int maxIpFailures;
    private final Duration failureWindow;
    private final Duration lockoutDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${app.security.login.max-user-ip-failures:5}") int maxUserIpFailures,
            @Value("${app.security.login.max-ip-failures:25}") int maxIpFailures,
            @Value("${app.security.login.failure-window-minutes:15}") long failureWindowMinutes,
            @Value("${app.security.login.lockout-minutes:15}") long lockoutMinutes
    ) {
        this(
                maxUserIpFailures,
                maxIpFailures,
                Duration.ofMinutes(failureWindowMinutes),
                Duration.ofMinutes(lockoutMinutes),
                Clock.systemUTC()
        );
    }

    LoginAttemptService(
            int maxUserIpFailures,
            int maxIpFailures,
            Duration failureWindow,
            Duration lockoutDuration,
            Clock clock
    ) {
        this.maxUserIpFailures = maxUserIpFailures;
        this.maxIpFailures = maxIpFailures;
        this.failureWindow = failureWindow;
        this.lockoutDuration = lockoutDuration;
        this.clock = clock;
    }

    public Optional<Duration> retryAfter(String username, String clientIp) {
        Instant now = Instant.now(clock);
        Optional<Duration> userIpRetry = retryAfterForKey(userIpKey(username, clientIp), now);
        Optional<Duration> ipRetry = retryAfterForKey(ipKey(clientIp), now);

        if (userIpRetry.isEmpty()) {
            return ipRetry;
        }

        if (ipRetry.isEmpty()) {
            return userIpRetry;
        }

        return Optional.of(
                userIpRetry.get().compareTo(ipRetry.get()) >= 0
                        ? userIpRetry.get()
                        : ipRetry.get()
        );
    }

    public void recordFailedLogin(String username, String clientIp) {
        Instant now = Instant.now(clock);
        recordFailure(userIpKey(username, clientIp), maxUserIpFailures, now);
        recordFailure(ipKey(clientIp), maxIpFailures, now);
    }

    public void recordSuccessfulLogin(String username, String clientIp) {
        attempts.remove(userIpKey(username, clientIp));
    }

    private Optional<Duration> retryAfterForKey(String key, Instant now) {
        AttemptState state = attempts.get(key);

        if (state == null) {
            return Optional.empty();
        }

        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(now)) {
                return Optional.of(Duration.between(now, state.lockedUntil));
            }

            attempts.remove(key);
            return Optional.empty();
        }

        if (state.firstFailureAt.plus(failureWindow).isBefore(now)) {
            attempts.remove(key);
        }

        return Optional.empty();
    }

    private void recordFailure(String key, int maxFailures, Instant now) {
        attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.firstFailureAt.plus(failureWindow).isBefore(now)) {
                return new AttemptState(1, now, null);
            }

            int failures = existing.failedAttempts + 1;
            Instant lockedUntil = failures >= maxFailures
                    ? now.plus(lockoutDuration)
                    : existing.lockedUntil;

            return new AttemptState(failures, existing.firstFailureAt, lockedUntil);
        });
    }

    private String userIpKey(String username, String clientIp) {
        String normalizedUsername = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);

        return "user-ip:" + normalizedUsername + "|" + normalizeIp(clientIp);
    }

    private String ipKey(String clientIp) {
        return "ip:" + normalizeIp(clientIp);
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private record AttemptState(int failedAttempts, Instant firstFailureAt, Instant lockedUntil) {
    }
}
