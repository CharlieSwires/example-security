/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.Properties;
import java.util.stream.Collectors;

record MonitorConfig(
        String healthUrl,
        String snapshotUrl,
        String monitoringKey,
        Duration pollInterval,
        Duration requestTimeout,
        int snapshotSamples,
        Set<String> expectedInstanceIds,
        int failuresBeforeAlert,
        long slowResponseMillis,
        double heapWarningPercent,
        double diskWarningPercent,
        long certificateWarningDays,
        boolean smsEnabled,
        String twilioAccountSid,
        String twilioAuthToken,
        String twilioFrom,
        String smsTo,
        Duration smsCooldown
) {
    static MonitorConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new MonitorConfig(
                required(properties, "health.url"),
                required(properties, "snapshot.url"),
                requiredEnvironment("MONITORING_API_KEY"),
                Duration.ofSeconds(integer(properties, "poll.seconds", 60, 5, 3600)),
                Duration.ofSeconds(integer(properties, "request.timeout.seconds", 10, 2, 120)),
                integer(properties, "snapshot.samples", 4, 1, 20),
                csv(properties.getProperty("expected.instance.ids", "")),
                integer(properties, "failures.before.alert", 3, 1, 20),
                integer(properties, "slow.response.millis", 2000, 100, 120000),
                decimal(properties, "heap.warning.percent", 85),
                decimal(properties, "disk.warning.percent", 80),
                integer(properties, "certificate.warning.days", 21, 1, 365),
                Boolean.parseBoolean(properties.getProperty("sms.enabled", "false")),
                environment("TWILIO_ACCOUNT_SID"),
                environment("TWILIO_AUTH_TOKEN"),
                environment("TWILIO_FROM_NUMBER"),
                environment("MONITOR_SMS_TO"),
                Duration.ofMinutes(integer(properties, "sms.cooldown.minutes", 30, 1, 1440))
        ).validate();
    }

    private MonitorConfig validate() {
        if (smsEnabled && (twilioAccountSid.isBlank() || twilioAuthToken.isBlank()
                || twilioFrom.isBlank() || smsTo.isBlank())) {
            throw new IllegalArgumentException("SMS is enabled but one or more Twilio environment variables are missing");
        }
        return this;
    }

    private static String required(Properties p, String name) {
        String value = p.getProperty(name, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing property: " + name);
        return value;
    }

    private static int integer(Properties p, String name, int fallback, int minimum, int maximum) {
        int value = Integer.parseInt(p.getProperty(name, Integer.toString(fallback)).trim());
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static double decimal(Properties p, String name, double fallback) {
        double value = Double.parseDouble(p.getProperty(name, Double.toString(fallback)).trim());
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be from 0 to 100");
        return value;
    }

    private static String requiredEnvironment(String name) {
        String value = environment(name);
        if (value.length() < 32) throw new IllegalArgumentException(name + " must be at least 32 characters");
        return value;
    }

    private static String environment(String name) {
        return System.getenv().getOrDefault(name, "").trim();
    }

    private static Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
