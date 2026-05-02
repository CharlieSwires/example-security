package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "users")
public class AppUser {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private boolean emailVerified;
    private String pendingEmail;
    private String emailVerificationTokenHash;
    private Instant emailVerificationExpiresAt;
    private String passwordResetTokenHash;
    private Instant passwordResetExpiresAt;
    private byte[] salt;
    private String hash;
    private Set<Role> roles = new HashSet<>();

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPendingEmail() { return pendingEmail; }
    public String getEmailVerificationTokenHash() { return emailVerificationTokenHash; }
    public Instant getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public String getPasswordResetTokenHash() { return passwordResetTokenHash; }
    public Instant getPasswordResetExpiresAt() { return passwordResetExpiresAt; }
    public byte[] getSalt() { return salt; }
    public String getHash() { return hash; }
    public Set<Role> getRoles() { return roles; }

    public void setId(String id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }
    public void setEmailVerificationTokenHash(String emailVerificationTokenHash) { this.emailVerificationTokenHash = emailVerificationTokenHash; }
    public void setEmailVerificationExpiresAt(Instant emailVerificationExpiresAt) { this.emailVerificationExpiresAt = emailVerificationExpiresAt; }
    public void setPasswordResetTokenHash(String passwordResetTokenHash) { this.passwordResetTokenHash = passwordResetTokenHash; }
    public void setPasswordResetExpiresAt(Instant passwordResetExpiresAt) { this.passwordResetExpiresAt = passwordResetExpiresAt; }
    public void setSalt(byte[] salt) { this.salt = salt; }
    public void setHash(String hash) { this.hash = hash; }
    public void setRoles(Set<Role> roles) { this.roles = roles == null ? new HashSet<>() : roles; }
}
