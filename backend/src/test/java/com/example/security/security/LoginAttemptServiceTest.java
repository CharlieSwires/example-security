package com.example.security.security;

import com.example.security.repository.LoginAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LoginAttemptServiceTest {

    @Test
    void locksUserIpAfterConfiguredFailures() {
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);

        LoginAttemptService service = new LoginAttemptService(
                repository,
                false,
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
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);

        LoginAttemptService service = new LoginAttemptService(
                repository,
                false,
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
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);

        LoginAttemptService service = new LoginAttemptService(
                repository,
                false,
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
}
