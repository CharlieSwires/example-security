/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.security.monitoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true")
public class MonitoringController {

    public static final String API_KEY_HEADER = "X-Monitoring-Key";

    private final MonitoringSnapshotService snapshots;
    private final byte[] configuredKey;

    public MonitoringController(
            MonitoringSnapshotService snapshots,
            @Value("${app.monitoring.api-key:}") String apiKey) {
        this.snapshots = snapshots;
        String candidate = apiKey == null ? "" : apiKey.trim();
        if (candidate.length() < 32 || placeholder(candidate)) {
            throw new IllegalStateException(
                    "MONITORING_API_KEY must be a non-placeholder secret of at least 32 characters when monitoring is enabled");
        }
        this.configuredKey = candidate.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/snapshot")
    public ResponseEntity<MonitoringSnapshot> snapshot(
            @RequestHeader(name = API_KEY_HEADER, required = false) String suppliedKey) {
        byte[] supplied = suppliedKey == null
                ? new byte[0]
                : suppliedKey.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(configuredKey, supplied)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(snapshots.capture());
    }

    private static boolean placeholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("replace-with") || lower.contains("change_me") || lower.contains("example");
    }
}
