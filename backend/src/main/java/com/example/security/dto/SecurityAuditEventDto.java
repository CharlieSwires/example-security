package com.example.security.dto;

import com.example.security.model.SecurityAuditEvent;

import java.time.Instant;
import java.util.Map;

public record SecurityAuditEventDto(
        String id,
        Instant timestamp,
        String eventType,
        String actor,
        String target,
        String clientIp,
        String userAgent,
        boolean success,
        String reason,
        Map<String, String> details
) {
    public static SecurityAuditEventDto from(SecurityAuditEvent event) {
        return new SecurityAuditEventDto(
                event.getId(),
                event.getTimestamp(),
                event.getEventType(),
                event.getActor(),
                event.getTarget(),
                event.getClientIp(),
                event.getUserAgent(),
                event.isSuccess(),
                event.getReason(),
                event.getDetails() == null ? Map.of() : event.getDetails()
        );
    }
}
