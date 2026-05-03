package com.example.security.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    @Test
    void locksUsernameAndIpAfterConfiguredFailures() {
        LoginAttemptService service = new LoginAttemptService(
                3,
                10,
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();

        service.recordFailedLogin("super", "127.0.0.1");
        service.recordFailedLogin("super", "127.0.0.1");
        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();

        service.recordFailedLogin("super", "127.0.0.1");
        assertThat(service.retryAfter("super", "127.0.0.1")).isPresent();
    }

    @Test
    void successfulLoginClearsUsernameAndIpCounter() {
        LoginAttemptService service = new LoginAttemptService(
                3,
                10,
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
        );

        service.recordFailedLogin("super", "127.0.0.1");
        service.recordFailedLogin("super", "127.0.0.1");
        service.recordSuccessfulLogin("super", "127.0.0.1");
        service.recordFailedLogin("super", "127.0.0.1");

        assertThat(service.retryAfter("super", "127.0.0.1")).isEmpty();
    }
}
