package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "offices")
public class OfficeAccount {
    @Id
    private String id;

    @Indexed(unique = true)
    private String officeId;

    @Indexed(unique = true, sparse = true)
    private String username;

    private byte[] salt;
    private String hash;

    private String displayName;
    private String addressEncrypted;
    private String telephoneEncrypted;
    private String email;
    private Instant createdAt;

    public String getId() { return id; }
    public String getOfficeId() { return officeId; }
    public String getUsername() { return username; }
    public byte[] getSalt() { return salt; }
    public String getHash() { return hash; }
    public String getDisplayName() { return displayName; }
    public String getAddressEncrypted() { return addressEncrypted; }
    public String getTelephoneEncrypted() { return telephoneEncrypted; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setOfficeId(String officeId) { this.officeId = officeId == null || officeId.isBlank() ? null : officeId.trim().toLowerCase(); }
    public void setUsername(String username) { this.username = username == null || username.isBlank() ? null : username.trim().toLowerCase(); }
    public void setSalt(byte[] salt) { this.salt = salt; }
    public void setHash(String hash) { this.hash = hash; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null || displayName.isBlank() ? null : displayName.trim(); }
    public void setAddressEncrypted(String addressEncrypted) { this.addressEncrypted = addressEncrypted; }
    public void setTelephoneEncrypted(String telephoneEncrypted) { this.telephoneEncrypted = telephoneEncrypted; }
    public void setEmail(String email) { this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase(); }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
