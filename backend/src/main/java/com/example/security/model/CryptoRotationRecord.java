package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "crypto_rotation_records")
public class CryptoRotationRecord {
    @Id
    private String id;

    @Indexed
    private String fromKeyFingerprint;
    @Indexed
    private String toKeyFingerprint;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private long usersRotated;
    private long officesRotated;
    private long appointmentsRotated;
    private long notesRotated;
    private String message;

    public String getId() { return id; }
    public String getFromKeyFingerprint() { return fromKeyFingerprint; }
    public String getToKeyFingerprint() { return toKeyFingerprint; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getUsersRotated() { return usersRotated; }
    public long getOfficesRotated() { return officesRotated; }
    public long getAppointmentsRotated() { return appointmentsRotated; }
    public long getNotesRotated() { return notesRotated; }
    public String getMessage() { return message; }

    public void setId(String id) { this.id = id; }
    public void setFromKeyFingerprint(String fromKeyFingerprint) { this.fromKeyFingerprint = fromKeyFingerprint; }
    public void setToKeyFingerprint(String toKeyFingerprint) { this.toKeyFingerprint = toKeyFingerprint; }
    public void setStatus(String status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setUsersRotated(long usersRotated) { this.usersRotated = usersRotated; }
    public void setOfficesRotated(long officesRotated) { this.officesRotated = officesRotated; }
    public void setAppointmentsRotated(long appointmentsRotated) { this.appointmentsRotated = appointmentsRotated; }
    public void setNotesRotated(long notesRotated) { this.notesRotated = notesRotated; }
    public void setMessage(String message) { this.message = message; }
}
