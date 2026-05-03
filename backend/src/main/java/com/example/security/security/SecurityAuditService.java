package com.example.security.security;

import com.example.security.model.SecurityAuditEvent;
import com.example.security.repository.SecurityAuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventRepository repository;
    private final boolean persistEvents;

    public SecurityAuditService(
            SecurityAuditEventRepository repository,
            @Value("${app.security.audit.persist:true}") boolean persistEvents
    ) {
        this.repository = repository;
        this.persistEvents = persistEvents;
    }

    public void record(
            String eventType,
            String actor,
            String target,
            boolean success,
            String reason,
            HttpServletRequest request
    ) {
        record(eventType, actor, target, success, reason, request, Map.of());
    }

    public void record(
            String eventType,
            String actor,
            String target,
            boolean success,
            String reason,
            HttpServletRequest request,
            Map<String, String> details
    ) {
        String clientIp = clientIp(request);
        String userAgent = request == null ? null : request.getHeader("User-Agent");

        log.info("security_event type={} actor={} target={} success={} reason={} ip={}",
                safe(eventType), safe(actor), safe(target), success, safe(reason), safe(clientIp));

        if (!persistEvents) {
            return;
        }

        try {
            SecurityAuditEvent event = new SecurityAuditEvent();
            event.setTimestamp(Instant.now());
            event.setEventType(eventType);
            event.setActor(actor);
            event.setTarget(target);
            event.setSuccess(success);
            event.setReason(reason);
            event.setClientIp(clientIp);
            event.setUserAgent(userAgent);
            event.setDetails(details == null ? Map.of() : details);
            repository.save(event);
        } catch (RuntimeException ex) {
            log.warn("Could not persist security audit event type={}", eventType, ex);
        }
    }

    public String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.replace('\n', '_').replace('\r', '_');
    }
}
