/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.repository.UserRepository;

class MfaServiceDstTransitionTest {
    private static final String RFC_COMPATIBLE_SECRET = "JBSWY3DPEHPK3PXP";
    private final TimeZone originalTimeZone = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void verifiesCodeImmediatelyBeforeUkReturnsToGmt() {
        MfaService service = serviceAt("2026-10-25T00:59:45Z");

        assertThat(service.verifySecret(RFC_COMPATIBLE_SECRET, "239700")).isTrue();
    }

    @Test
    void verifiesCodeImmediatelyAfterUkReturnsToGmt() {
        MfaService service = serviceAt("2026-10-25T01:00:15Z");

        assertThat(service.verifySecret(RFC_COMPATIBLE_SECRET, "398755")).isTrue();
    }

    @Test
    void verificationDoesNotDependOnJvmDefaultTimeZone() {
        Clock instant = Clock.fixed(Instant.parse("2026-10-25T01:00:15Z"), ZoneOffset.UTC);
        MfaService utcService = serviceWith(instant);
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/London")));
        MfaService londonService = serviceWith(instant);

        assertThat(utcService.verifySecret(RFC_COMPATIBLE_SECRET, "398755")).isTrue();
        assertThat(londonService.verifySecret(RFC_COMPATIBLE_SECRET, "398755")).isTrue();
    }

    @Test
    void doesNotAcceptTheCodeFromOneHourAway() {
        MfaService service = serviceAt("2026-10-25T01:00:15Z");

        assertThat(service.verifySecret(RFC_COMPATIBLE_SECRET, "408700")).isFalse();
    }

    private MfaService serviceAt(String instant) {
        return serviceWith(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private MfaService serviceWith(Clock clock) {
        return new MfaService(mock(UserRepository.class), mock(FieldCryptoService.class),
                mock(MongoOperations.class), clock);
    }
}
