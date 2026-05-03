package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "login_attempts")
public class LoginAttempt {
    @Id
    private String key;

    private int failedAttempts;
    private Instant firstFailureAt;
    private Instant lockedUntil;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public String getKey() { return key; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getFirstFailureAt() { return firstFailureAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setKey(String key) { this.key = key; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public void setFirstFailureAt(Instant firstFailureAt) { this.firstFailureAt = firstFailureAt; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
