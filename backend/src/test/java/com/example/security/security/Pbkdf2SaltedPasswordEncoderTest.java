package com.example.security.security;

import com.example.security.service.UserService;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2SaltedPasswordEncoderTest {

    @Test
    void matchesPasswordAgainstStoredSaltAndHash() {
        Pbkdf2SaltedPasswordEncoder encoder = new Pbkdf2SaltedPasswordEncoder();

        String encoded = encoder.encode("correct-password");

        assertThat(encoder.matches("correct-password", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
        assertThat(encoded.split(":", 3)[1])
                .isEqualTo(Integer.toString(UserService.CURRENT_PASSWORD_ITERATIONS));
    }

    @Test
    void samePasswordGetsDifferentStoredValueBecauseSaltChanges() {
        Pbkdf2SaltedPasswordEncoder encoder = new Pbkdf2SaltedPasswordEncoder();

        String first = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("same-password", first)).isTrue();
        assertThat(encoder.matches("same-password", second)).isTrue();
    }

    @Test
    void verifiesLegacyHashSoItCanBeUpgradedAfterLogin() {
        Pbkdf2SaltedPasswordEncoder encoder = new Pbkdf2SaltedPasswordEncoder();
        byte[] salt = new byte[20];
        new SecureRandom().nextBytes(salt);
        String hash = UserService.hashPassword(
                salt, "legacy-password", UserService.LEGACY_PASSWORD_ITERATIONS);
        String encoded = UserService.encodedPasswordForSpringSecurity(
                salt, hash, UserService.LEGACY_PASSWORD_ITERATIONS);

        assertThat(encoder.matches("legacy-password", encoded)).isTrue();
    }
}
