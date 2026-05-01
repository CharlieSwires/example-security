package com.example.security.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2SaltedPasswordEncoderTest {

    @Test
    void matchesPasswordAgainstStoredSaltAndHash() {
        Pbkdf2SaltedPasswordEncoder encoder = new Pbkdf2SaltedPasswordEncoder();

        String encoded = encoder.encode("correct-password");

        assertThat(encoder.matches("correct-password", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }
}
