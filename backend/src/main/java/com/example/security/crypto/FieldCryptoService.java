package com.example.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Locale;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class FieldCryptoService {
    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int ITERATIONS = 600_000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;
    private final SecretKeySpec lookupKey;
    private final boolean enabled;

    @Autowired
    public FieldCryptoService(
            @Value("${app.crypto.enabled:true}") boolean enabled,
            @Value("${app.crypto.passphrase:}") String passphrase,
            @Value("${app.crypto.master-salt:ZXhhbXBsZS1zZWN1cml0eS1kZXYtc2FsdC0yMDI2}") String masterSaltB64
    ) {
        this(enabled, passphrase, masterSaltB64, true);
    }

    private FieldCryptoService(boolean enabled, String passphrase, String masterSaltB64, boolean direct) {
        this.enabled = enabled;
        if (!enabled) {
            this.key = null;
            this.lookupKey = null;
            return;
        }
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException("FIELD_CRYPTO_PASSPHRASE/app.crypto.passphrase must be set when field encryption is enabled");
        }
        byte[] salt = Base64.getDecoder().decode(masterSaltB64);
        this.key = deriveKey(passphrase.toCharArray(), salt, "field-encryption", "AES");
        this.lookupKey = deriveKey(passphrase.toCharArray(), salt, "field-lookup-hmac", "HmacSHA256");
    }

    public static FieldCryptoService forPassphrase(String passphrase, String masterSaltB64) {
        return new FieldCryptoService(true, passphrase, masterSaltB64, true);
    }

    public static String fingerprintFor(String passphrase, String masterSaltB64) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalArgumentException("Passphrase is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("field-crypto-passphrase-fingerprint-v1:" + masterSaltB64 + ":" + passphrase).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Could not fingerprint field crypto passphrase", e);
        }
    }

    public String encryptNullable(String plaintext) {
        if (plaintext == null) return null;
        if (!enabled) return plaintext;
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt field", e);
        }
    }

    public String decryptNullable(String stored) {
        if (stored == null) return null;
        if (!enabled || !stored.startsWith(PREFIX)) return stored;
        try {
            String payload = stored.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted field payload");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt field. Check FIELD_CRYPTO_PASSPHRASE and FIELD_CRYPTO_MASTER_SALT_B64.", e);
        }
    }

    public String encryptBlankAsNull(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        return encryptNullable(plaintext.trim());
    }

    /**
     * Deterministic keyed lookup token for exact searching/grouping without storing plaintext.
     * This is not reversible like encryption, but anyone with the secret can test guesses,
     * so only use it for low-volume exact lookups and keep the key secret.
     */
    public String lookupHashNullable(String plaintext) {
        String normalized = normalizeForLookup(plaintext);
        if (normalized == null) return null;
        if (!enabled) return "plain-lookup:" + normalized;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookupKey);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return "hmac:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create field lookup hash", e);
        }
    }

    private String normalizeForLookup(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static SecretKeySpec deriveKey(char[] passphrase, byte[] salt, String purpose, String algorithm) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] purposeBytes = purpose.getBytes(StandardCharsets.UTF_8);
            byte[] purposeSalt = new byte[salt.length + purposeBytes.length];
            System.arraycopy(salt, 0, purposeSalt, 0, salt.length);
            System.arraycopy(purposeBytes, 0, purposeSalt, salt.length, purposeBytes.length);
            KeySpec spec = new PBEKeySpec(passphrase, purposeSalt, ITERATIONS, KEY_BITS);
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(encoded, algorithm);
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive field encryption key", e);
        }
    }
}
