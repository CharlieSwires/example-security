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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAttemptServiceTest {

    @Test
    void locksUserIpAfterConfiguredFailures() {
        LoginAttemptRepository repository = persistentRepository();

        LoginAttemptService service = new LoginAttemptService(
                repository,
                2,
                10,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-03T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();

        service.recordFailedLogin("super", "127.0.0.1");
        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();

        service.recordFailedLogin("super", "127.0.0.1");
        assertThat(service.retryAfter("super", "127.0.0.1")).isPresent();
    }

    @Test
    void successfulLoginClearsUserIpFailureCounter() {
        LoginAttemptRepository repository = persistentRepository();

        LoginAttemptService service = new LoginAttemptService(
                repository,
                2,
                10,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-03T12:00:00Z"), ZoneOffset.UTC)
        );

        service.recordFailedLogin("super", "127.0.0.1");
        service.recordSuccessfulLogin("super", "127.0.0.1");

        service.recordFailedLogin("super", "127.0.0.1");

        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();
    }

    @Test
    void locksIpAfterConfiguredFailuresAcrossUsernames() {
        LoginAttemptRepository repository = persistentRepository();

        LoginAttemptService service = new LoginAttemptService(
                repository,
                10,
                2,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-03T12:00:00Z"), ZoneOffset.UTC)
        );

        service.recordFailedLogin("super", "127.0.0.1");
        assertThat(service.retryAfter("other", "127.0.0.1")).isEmpty();

        service.recordFailedLogin("admin", "127.0.0.1");
        assertThat(service.retryAfter("other", "127.0.0.1")).isPresent();
    }

    private LoginAttemptRepository persistentRepository() {
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);
        Map<String, LoginAttempt> records = new HashMap<>();
        when(repository.findById(any(String.class))).thenAnswer(invocation ->
                Optional.ofNullable(records.get(invocation.getArgument(0))));
        when(repository.save(any(LoginAttempt.class))).thenAnswer(invocation -> {
            LoginAttempt attempt = invocation.getArgument(0);
            records.put(attempt.getKey(), attempt);
            return attempt;
        });
        doAnswer(invocation -> {
            records.remove(invocation.getArgument(0));
            return null;
        }).when(repository).deleteById(any(String.class));
        return repository;
    }
}
