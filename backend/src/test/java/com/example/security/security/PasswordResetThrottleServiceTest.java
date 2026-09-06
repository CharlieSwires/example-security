package com.example.security.security;

import com.example.security.model.LoginAttempt;
import com.example.security.repository.LoginAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordResetThrottleServiceTest {
    @Test
    void locksAnAccountAfterConfiguredNumberOfRequests() {
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);
        Map<String, LoginAttempt> records = new HashMap<>();
        when(repository.findById(any(String.class))).thenAnswer(invocation ->
                Optional.ofNullable(records.get(invocation.getArgument(0))));
        when(repository.save(any(LoginAttempt.class))).thenAnswer(invocation -> {
            LoginAttempt attempt = invocation.getArgument(0);
            records.put(attempt.getKey(), attempt);
            return attempt;
        });

        PasswordResetThrottleService service = new PasswordResetThrottleService(
                repository, 2, 10, Duration.ofHours(1), Duration.ofHours(1),
                Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC));

        service.recordRequest("patient@example.test", "127.0.0.1");
        assertThat(service.retryAfter("patient@example.test", "127.0.0.1")).isEmpty();
        service.recordRequest("patient@example.test", "127.0.0.1");
        assertThat(service.retryAfter("patient@example.test", "127.0.0.1")).isPresent();
    }
}
