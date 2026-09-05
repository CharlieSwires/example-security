package com.example.security.security;

import com.example.security.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class Pbkdf2SaltedPasswordEncoder implements PasswordEncoder {

    private static final int SALT_BYTES = 20;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);

        String hash = UserService.hashPassword(salt, rawPassword.toString());
        return UserService.encodedPasswordForSpringSecurity(
                salt, hash, UserService.CURRENT_PASSWORD_ITERATIONS);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || !encodedPassword.contains(":")) {
            return false;
        }

        String[] parts = encodedPassword.split(":", 3);
        if (parts.length != 3) {
            return false;
        }

        byte[] salt;
        int iterations;
        try {
            salt = Base64.getDecoder().decode(parts[0]);
            iterations = Integer.parseInt(parts[1]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String expectedHash = parts[2];
        String actualHash = UserService.hashPassword(salt, rawPassword.toString(), iterations);

        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
