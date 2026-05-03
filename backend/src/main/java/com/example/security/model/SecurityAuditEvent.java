package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "security_audit_events")
public class SecurityAuditEvent {
    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    @Indexed
    private String eventType;

    @Indexed
    private String actor;

    @Indexed
    private String target;

    private String clientIp;
    private String userAgent;
    private boolean success;
    private String reason;
    private Map<String, String> details;

    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getTarget() { return target; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }
    public Map<String, String> getDetails() { return details; }

    public void setId(String id) { this.id = id; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setActor(String actor) { this.actor = actor; }
    public void setTarget(String target) { this.target = target; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDetails(Map<String, String> details) { this.details = details; }
}
