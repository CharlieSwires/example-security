package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.model.AppUser;
import com.example.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaServiceTest {

    @Test
    void newRecoveryCodesHaveIndependentSalts() {
        UserRepository users = mock(UserRepository.class);
        FieldCryptoService crypto = mock(FieldCryptoService.class);
        AppUser user = new AppUser();
        user.setUsername("alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(crypto.encryptNullable("secret")).thenReturn("encrypted-secret");

        MfaService service = new MfaService(users, crypto, mock(MongoOperations.class));
        service.enable("alice", "secret");

        assertThat(user.getRecoveryCodeHashes())
                .hasSize(10)
                .allMatch(hash -> hash.startsWith("sha256:v2:"));
        assertThat(user.getRecoveryCodeHashes()).doesNotHaveDuplicates();
        assertThat(user.getRecoveryCodeHashes()).noneMatch(hash -> hash.contains("-"));
    }
}
