package com.example.security.service;

import com.example.security.dto.UserDto;
import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.repository.UserRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {
    private static final int SALT_BYTES = 20;
    private static final int ITERATIONS = 65_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final Duration EMAIL_TOKEN_LIFETIME = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final int MIN_PASSWORD_LENGTH = 12;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;
    private final String backendBaseUrl;

    public UserService(UserRepository userRepository,
                       EmailService emailService,
                       @Value("${app.frontend-base-url}") String frontendBaseUrl,
                       @Value("${app.backend-base-url}") String backendBaseUrl) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.backendBaseUrl = backendBaseUrl;
    }

    public AppUser createUser(String username, String password, String proposedEmail, Set<Role> roles) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        validatePassword(password);
        if (userRepository.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username already exists");

        byte[] salt = newSalt();
        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setSalt(salt);
        user.setHash(hashPassword(salt, password));
        user.setRoles(roles == null || roles.isEmpty() ? Set.of(Role.USER) : roles);

        AppUser saved = userRepository.save(user);
        if (proposedEmail != null && !proposedEmail.isBlank()) {
            proposeEmail(saved.getUsername(), proposedEmail);
            return userRepository.findByUsername(saved.getUsername()).orElse(saved);
        }
        return saved;
    }

    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<UserDto> findAllUsers() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public UserDto updateRoles(String username, Set<Role> roles) {
        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Set<Role> nextRoles = roles == null || roles.isEmpty() ? Set.of(Role.USER) : roles;
        if (user.getRoles().contains(Role.SUPER) && !nextRoles.contains(Role.SUPER)
                && userRepository.countByRolesContaining(Role.SUPER) <= 1) {
            throw new IllegalArgumentException("Cannot remove SUPER from the last SUPER user");
        }
        user.setRoles(nextRoles);
        return toDto(userRepository.save(user));
    }

    public UserDto updatePassword(String username, String newPassword) {
        validatePassword(newPassword);
        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        setPassword(user, newPassword);
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        return toDto(userRepository.save(user));
    }

    public UserDto proposeEmail(String username, String proposedEmail) {
        if (proposedEmail == null || proposedEmail.isBlank() || !proposedEmail.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }

        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        String normalizedEmail = proposedEmail.trim().toLowerCase();
        String rawToken = newToken();

        user.setPendingEmail(normalizedEmail);
        user.setEmailVerificationTokenHash(hashToken(rawToken));
        user.setEmailVerificationExpiresAt(Instant.now().plus(EMAIL_TOKEN_LIFETIME));
        AppUser saved = userRepository.save(user);

        String verificationUrl = UriComponentsBuilder.fromHttpUrl(backendBaseUrl)
                .path("/api/email/verify")
                .queryParam("token", rawToken)
                .toUriString();

        emailService.sendVerificationEmail(normalizedEmail, verificationUrl);
        return toDto(saved);
    }

    public boolean verifyEmailToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        Optional<AppUser> found = userRepository.findByEmailVerificationTokenHash(hashToken(rawToken));
        if (found.isEmpty()) return false;

        AppUser user = found.get();
        if (user.getEmailVerificationExpiresAt() == null || user.getEmailVerificationExpiresAt().isBefore(Instant.now())) return false;

        user.setEmail(user.getPendingEmail());
        user.setEmailVerified(true);
        user.setPendingEmail(null);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);
        return true;
    }

    public void sendForgotPasswordEmailIfVerifiedEmailExists(String email) {
        if (email == null || email.isBlank()) return;
        userRepository.findByEmailAndEmailVerifiedTrue(email.trim().toLowerCase()).ifPresent(this::sendPasswordResetEmail);
    }

    public void sendPasswordChangeLinkForUsername(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (user.getEmail() == null || !user.isEmailVerified()) {
            throw new IllegalStateException("No verified email is available for this user");
        }
        sendPasswordResetEmail(user);
    }

    private void sendPasswordResetEmail(AppUser user) {
        String rawToken = newToken();
        user.setPasswordResetTokenHash(hashToken(rawToken));
        user.setPasswordResetExpiresAt(Instant.now().plus(PASSWORD_RESET_TOKEN_LIFETIME));
        userRepository.save(user);

        String resetUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .toUriString();

        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
    }

    public boolean resetPasswordWithToken(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || newPassword == null || newPassword.isBlank()) return false;
        if (!isPasswordAcceptable(newPassword)) return false;
        Optional<AppUser> found = userRepository.findByPasswordResetTokenHash(hashToken(rawToken));
        if (found.isEmpty()) return false;

        AppUser user = found.get();
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(Instant.now())) return false;

        setPassword(user, newPassword);
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
        return true;
    }

    public void deleteByUsername(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (user.getRoles().contains(Role.SUPER) && userRepository.countByRolesContaining(Role.SUPER) <= 1) {
            throw new IllegalArgumentException("Cannot delete the last SUPER user");
        }
        userRepository.deleteByUsername(username);
    }

    public UserDto toDto(AppUser user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.isEmailVerified(), user.getPendingEmail(), user.getRoles());
    }


    private void validatePassword(String password) {
        if (!isPasswordAcceptable(password)) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private boolean isPasswordAcceptable(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    private void setPassword(AppUser user, String newPassword) {
        byte[] salt = newSalt();
        user.setSalt(salt);
        user.setHash(hashPassword(salt, newPassword));
    }

    private byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash token", e);
        }
    }

    public static String hashPassword(byte[] salt, String password) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKey secretKey = factory.generateSecret(keySpec);
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash password", e);
        }
    }

    public static String encodedPasswordForSpringSecurity(byte[] salt, String hash) {
        return Base64.getEncoder().encodeToString(salt) + ":" + hash;
    }
}
