package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.MfaSetupResponse;
import com.example.security.model.AppUser;
import com.example.security.repository.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MfaService {
    private static final String ISSUER = "Ophthalmic Clinics";
    private static final int SECRET_BYTES = 20;
    private static final int TOTP_DIGITS = 6;
    private static final long TOTP_PERIOD_SECONDS = 30;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 10;
    private static final int RECOVERY_HASH_SALT_BYTES = 16;
    private static final String RECOVERY_HASH_PREFIX = "sha256:v2:";
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final UserRepository userRepository;
    private final FieldCryptoService crypto;
    private final MongoOperations mongoOperations;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(UserRepository userRepository, FieldCryptoService crypto,
                      MongoOperations mongoOperations) {
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.mongoOperations = mongoOperations;
    }

    public boolean isEnabled(String username) {
        return userRepository.findByUsername(username).map(AppUser::isMfaEnabled).orElse(false);
    }

    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public MfaSetupResponse setupResponse(String username, String secret) {
        String label = ISSUER + ":" + username;
        String uri = "otpauth://totp/" + urlEncode(label)
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1&digits=6&period=30";
        return new MfaSetupResponse(secret, uri, qrDataUrl(uri));
    }

    public boolean verifySecret(String secret, String code) {
        String normalized = normalizeTotp(code);
        if (normalized == null) return false;
        long counter = Instant.now().getEpochSecond() / TOTP_PERIOD_SECONDS;
        // Accept one step either side for small clock differences.
        for (long candidate = counter - 1; candidate <= counter + 1; candidate++) {
            if (constantTimeEquals(normalized, totp(secret, candidate))) return true;
        }
        return false;
    }

    public List<String> enable(String username, String secret) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        List<String> recoveryCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = newRecoveryCode();
            recoveryCodes.add(code);
            hashes.add(hashRecoveryCodeForStorage(code));
        }

        user.setTotpSecretEncrypted(crypto.encryptNullable(secret));
        user.setRecoveryCodeHashes(hashes);
        user.setMfaEnabled(true);
        user.setMfaEnrolledAt(Instant.now());
        userRepository.save(user);
        return recoveryCodes;
    }

    public boolean verifyForLogin(String username, String suppliedCode) {
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isMfaEnabled() || user.getTotpSecretEncrypted() == null) return false;

        String secret = crypto.decryptNullable(user.getTotpSecretEncrypted());
        if (verifySecret(secret, suppliedCode)) return true;

        List<String> recoveryHashes = user.getRecoveryCodeHashes();
        if (recoveryHashes == null) return false;
        for (int i = 0; i < recoveryHashes.size(); i++) {
            if (matchesRecoveryCode(recoveryHashes.get(i), suppliedCode)) {
                String matchedHash = recoveryHashes.get(i);
                Query query = Query.query(Criteria.where("_id").is(user.getId())
                        .and("recoveryCodeHashes").is(matchedHash));
                return mongoOperations.updateFirst(query,
                        new Update().pull("recoveryCodeHashes", matchedHash), AppUser.class)
                        .getModifiedCount() == 1;
            }
        }
        return false;
    }

    public boolean disable(String username, String suppliedCode) {
        if (!verifyForLogin(username, suppliedCode)) return false;
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setMfaEnabled(false);
        user.setTotpSecretEncrypted(null);
        user.setRecoveryCodeHashes(new ArrayList<>());
        user.setMfaEnrolledAt(null);
        userRepository.save(user);
        return true;
    }

    private String totp(String base32Secret, long counter) {
        try {
            byte[] key = base32Decode(base32Secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hmac = mac.doFinal(counterBytes);
            int offset = hmac[hmac.length - 1] & 0x0f;
            int binary = ((hmac[offset] & 0x7f) << 24)
                    | ((hmac[offset + 1] & 0xff) << 16)
                    | ((hmac[offset + 2] & 0xff) << 8)
                    | (hmac[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate TOTP", e);
        }
    }

    private String qrDataUrl(String uri) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(uri, BarcodeFormat.QR_CODE, 300, 300,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create MFA QR code", e);
        }
    }

    private String newRecoveryCode() {
        byte[] bytes = new byte[RECOVERY_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String hex = HexFormat.of().withUpperCase().formatHex(bytes);
        return hex.substring(0, 5) + "-" + hex.substring(5, 10) + "-" + hex.substring(10, 15) + "-" + hex.substring(15, 20);
    }

    private String hashRecoveryCodeForStorage(String code) {
        byte[] salt = new byte[RECOVERY_HASH_SALT_BYTES];
        secureRandom.nextBytes(salt);
        return RECOVERY_HASH_PREFIX + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(recoveryDigest(salt, code));
    }

    private boolean matchesRecoveryCode(String storedHash, String suppliedCode) {
        if (storedHash == null || suppliedCode == null) return false;
        if (storedHash.startsWith(RECOVERY_HASH_PREFIX)) {
            try {
                String[] parts = storedHash.substring(RECOVERY_HASH_PREFIX.length()).split(":", 2);
                if (parts.length != 2) return false;
                byte[] salt = Base64.getDecoder().decode(parts[0]);
                String candidate = Base64.getEncoder().encodeToString(recoveryDigest(salt, suppliedCode));
                return constantTimeEquals(parts[1], candidate);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        // Backward compatibility: consume existing unsalted recovery-code hashes.
        return constantTimeEquals(storedHash, legacyRecoveryHash(suppliedCode));
    }

    private byte[] recoveryDigest(byte[] salt, String code) {
        String normalized = normalizeRecoveryCode(code);
        if (normalized == null) return new byte[0];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash MFA recovery code", e);
        }
    }

    private String legacyRecoveryHash(String code) {
        String normalized = normalizeRecoveryCode(code);
        if (normalized == null) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash MFA recovery code", e);
        }
    }

    private String normalizeRecoveryCode(String code) {
        if (code == null || code.isBlank()) return null;
        String normalized = code.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[0-9A-F]{20}") ? normalized : null;
    }

    private String normalizeTotp(String code) {
        if (code == null) return null;
        String normalized = code.replace(" ", "").trim();
        return normalized.matches("\\d{6}") ? normalized : null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte datum : data) {
            buffer = (buffer << 8) | (datum & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) result.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1f]);
        return result.toString();
    }

    private byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            int val;
            if (c >= 'A' && c <= 'Z') val = c - 'A';
            else if (c >= '2' && c <= '7') val = c - '2' + 26;
            else throw new IllegalArgumentException("Invalid Base32 character");
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
