package com.example.security.security;

import com.example.security.model.LoginAttempt;
import com.example.security.repository.LoginAttemptRepository;
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

    private final Map<String, AttemptState> inMemoryAttempts = new ConcurrentHashMap<>();
    private final LoginAttemptRepository repository;
    private final boolean persistent;
    private final int maxUserIpFailures;
    private final int maxIpFailures;
    private final Duration failureWindow;
    private final Duration lockoutDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            LoginAttemptRepository repository,
            @Value("${app.security.login.persistent:true}") boolean persistent,
            @Value("${app.security.login.max-user-ip-failures:5}") int maxUserIpFailures,
            @Value("${app.security.login.max-ip-failures:25}") int maxIpFailures,
            @Value("${app.security.login.failure-window-minutes:15}") long failureWindowMinutes,
            @Value("${app.security.login.lockout-minutes:15}") long lockoutMinutes
    ) {
        this(repository, persistent, maxUserIpFailures, maxIpFailures,
                Duration.ofMinutes(failureWindowMinutes), Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    LoginAttemptService(
            LoginAttemptRepository repository,
            boolean persistent,
            int maxUserIpFailures,
            int maxIpFailures,
            Duration failureWindow,
            Duration lockoutDuration,
            Clock clock
    ) {
        this.repository = repository;
        this.persistent = persistent;
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

        if (userIpRetry.isEmpty()) return ipRetry;
        if (ipRetry.isEmpty()) return userIpRetry;
        return Optional.of(userIpRetry.get().compareTo(ipRetry.get()) >= 0 ? userIpRetry.get() : ipRetry.get());
    }

    public void recordFailedLogin(String username, String clientIp) {
        Instant now = Instant.now(clock);
        recordFailure(userIpKey(username, clientIp), maxUserIpFailures, now);
        recordFailure(ipKey(clientIp), maxIpFailures, now);
    }

    public void recordSuccessfulLogin(String username, String clientIp) {
        String userIpKey = userIpKey(username, clientIp);
        if (persistent) {
            repository.deleteById(userIpKey);
        } else {
            inMemoryAttempts.remove(userIpKey);
        }
    }

    private Optional<Duration> retryAfterForKey(String key, Instant now) {
        AttemptState state = readState(key, now);
        if (state == null) return Optional.empty();

        if (state.lockedUntil != null) {
            if (state.lockedUntil.isAfter(now)) {
                return Optional.of(Duration.between(now, state.lockedUntil));
            }
            removeState(key);
        }
        return Optional.empty();
    }

    private void recordFailure(String key, int maxFailures, Instant now) {
        if (persistent) {
            LoginAttempt existing = repository.findById(key).orElse(null);
            LoginAttempt next = new LoginAttempt();
            next.setKey(key);

            if (existing == null || existing.getFirstFailureAt() == null || existing.getFirstFailureAt().plus(failureWindow).isBefore(now)) {
                next.setFailedAttempts(1);
                next.setFirstFailureAt(now);
            } else {
                int failures = existing.getFailedAttempts() + 1;
                next.setFailedAttempts(failures);
                next.setFirstFailureAt(existing.getFirstFailureAt());
                next.setLockedUntil(failures >= maxFailures ? now.plus(lockoutDuration) : existing.getLockedUntil());
            }

            Instant expiryBase = next.getLockedUntil() != null ? next.getLockedUntil() : next.getFirstFailureAt().plus(failureWindow);
            next.setExpiresAt(expiryBase.plus(Duration.ofMinutes(5)));
            repository.save(next);
            return;
        }

        inMemoryAttempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.firstFailureAt.plus(failureWindow).isBefore(now)) {
                return new AttemptState(1, now, null);
            }
            int failures = existing.failedAttempts + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockoutDuration) : existing.lockedUntil;
            return new AttemptState(failures, existing.firstFailureAt, lockedUntil);
        });
    }

    private AttemptState readState(String key, Instant now) {
        if (persistent) {
            LoginAttempt attempt = repository.findById(key).orElse(null);
            if (attempt == null) return null;
            if (attempt.getFirstFailureAt() == null || attempt.getFirstFailureAt().plus(failureWindow).isBefore(now)) {
                repository.deleteById(key);
                return null;
            }
            return new AttemptState(attempt.getFailedAttempts(), attempt.getFirstFailureAt(), attempt.getLockedUntil());
        }
        AttemptState state = inMemoryAttempts.get(key);
        if (state != null && state.firstFailureAt.plus(failureWindow).isBefore(now)) {
            inMemoryAttempts.remove(key);
            return null;
        }
        return state;
    }

    private void removeState(String key) {
        if (persistent) repository.deleteById(key); else inMemoryAttempts.remove(key);
    }

    private String userIpKey(String username, String clientIp) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "user-ip:" + normalizedUsername + "|" + normalizeIp(clientIp);
    }

    private String ipKey(String clientIp) {
        return "ip:" + normalizeIp(clientIp);
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private record AttemptState(int failedAttempts, Instant firstFailureAt, Instant lockedUntil) { }
}
