package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    /** PBKDF2 iteration count used for this password hash. Null/zero means the legacy 65,000 count. */
    private Integer passwordIterations;
    private Set<Role> roles = new HashSet<>();

    /** Office/clinic boundary for PATIENT, OFFICE and OFFICE_ADMIN users. HQ/SUPER may be blank. */
    private String officeId;

    /** Encrypted-at-rest actual person details. */
    private String displayNameEncrypted;
    /** Deterministic HMAC lookup token; never stores the plaintext display name. */
    @Indexed
    private String displayNameLookupHash;
    private String telephoneEncrypted;

    /** Authenticator-app MFA (RFC 6238 TOTP). Secret is encrypted with FieldCryptoService. */
    private boolean mfaEnabled;
    private String totpSecretEncrypted;
    private List<String> recoveryCodeHashes = new ArrayList<>();
    private Instant mfaEnrolledAt;

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
    public Integer getPasswordIterations() { return passwordIterations; }
    public Set<Role> getRoles() { return roles; }
    public String getOfficeId() { return officeId; }
    public String getDisplayNameEncrypted() { return displayNameEncrypted; }
    public String getDisplayNameLookupHash() { return displayNameLookupHash; }
    public String getTelephoneEncrypted() { return telephoneEncrypted; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public String getTotpSecretEncrypted() { return totpSecretEncrypted; }
    public List<String> getRecoveryCodeHashes() { return recoveryCodeHashes; }
    public Instant getMfaEnrolledAt() { return mfaEnrolledAt; }

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
    public void setPasswordIterations(Integer passwordIterations) { this.passwordIterations = passwordIterations; }
    public void setRoles(Set<Role> roles) { this.roles = roles == null ? new HashSet<>() : roles; }
    public void setOfficeId(String officeId) { this.officeId = officeId == null || officeId.isBlank() ? null : officeId.trim().toLowerCase(); }
    public void setDisplayNameEncrypted(String displayNameEncrypted) { this.displayNameEncrypted = displayNameEncrypted; }
    public void setDisplayNameLookupHash(String displayNameLookupHash) { this.displayNameLookupHash = displayNameLookupHash; }
    public void setTelephoneEncrypted(String telephoneEncrypted) { this.telephoneEncrypted = telephoneEncrypted; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public void setTotpSecretEncrypted(String totpSecretEncrypted) { this.totpSecretEncrypted = totpSecretEncrypted; }
    public void setRecoveryCodeHashes(List<String> recoveryCodeHashes) { this.recoveryCodeHashes = recoveryCodeHashes == null ? new ArrayList<>() : recoveryCodeHashes; }
    public void setMfaEnrolledAt(Instant mfaEnrolledAt) { this.mfaEnrolledAt = mfaEnrolledAt; }
}
