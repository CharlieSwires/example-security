/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies a single UTC clock for security protocols based on Unix time. */
@Configuration
public class UtcClockConfiguration {

    @Bean
    Clock securityClock() {
        return Clock.systemUTC();
    }
}
