package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.CryptoKeyRotationRequest;
import com.example.security.model.AppUser;
import com.example.security.repository.CryptoRotationRecordRepository;
import com.example.security.repository.OfficeAccountRepository;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import com.example.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryptoRotationServiceTest {
    private static final String MASTER_SALT = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void rotatesTotpSecretsAndReadsRepositoriesInPages() {
        UserRepository users = mock(UserRepository.class);
        OfficeAccountRepository offices = mock(OfficeAccountRepository.class);
        PatientAppointmentDocumentRepository appointments = mock(PatientAppointmentDocumentRepository.class);
        CryptoRotationRecordRepository rotations = mock(CryptoRotationRecordRepository.class);

        FieldCryptoService oldCrypto = FieldCryptoService.forPassphrase("old passphrase", MASTER_SALT);
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setTotpSecretEncrypted(oldCrypto.encryptNullable("JBSWY3DPEHPK3PXP"));

        when(rotations.existsById(any(String.class))).thenReturn(false);
        when(users.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(offices.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(appointments.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CryptoRotationService service = new CryptoRotationService(
                users, offices, appointments, rotations,
                MASTER_SALT, "old passphrase", 100);
        service.rotate(new CryptoKeyRotationRequest("old passphrase", "new passphrase"));

        FieldCryptoService newCrypto = FieldCryptoService.forPassphrase("new passphrase", MASTER_SALT);
        assertThat(newCrypto.decryptNullable(user.getTotpSecretEncrypted()))
                .isEqualTo("JBSWY3DPEHPK3PXP");
        verify(users).findAll(any(Pageable.class));
        verify(users, never()).findAll();
        verify(offices, never()).findAll();
        verify(appointments, never()).findAll();
    }
}
