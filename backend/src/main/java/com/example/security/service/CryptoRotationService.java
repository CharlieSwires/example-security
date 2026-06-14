package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.CryptoKeyRotationRequest;
import com.example.security.dto.CryptoKeyRotationResponse;
import com.example.security.model.AppUser;
import com.example.security.model.CryptoRotationRecord;
import com.example.security.model.OfficeAccount;
import com.example.security.model.PatientAppointmentDocument;
import com.example.security.repository.CryptoRotationRecordRepository;
import com.example.security.repository.OfficeAccountRepository;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import com.example.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CryptoRotationService {
    private final UserRepository userRepository;
    private final OfficeAccountRepository officeRepository;
    private final PatientAppointmentDocumentRepository appointmentRepository;
    private final CryptoRotationRecordRepository rotationRepository;
    private final String masterSaltB64;
    private final String activePassphrase;

    public CryptoRotationService(
            UserRepository userRepository,
            OfficeAccountRepository officeRepository,
            PatientAppointmentDocumentRepository appointmentRepository,
            CryptoRotationRecordRepository rotationRepository,
            @Value("${app.crypto.master-salt:ZXhhbXBsZS1zZWN1cml0eS1kZXYtc2FsdC0yMDI2}") String masterSaltB64,
            @Value("${app.crypto.passphrase:}") String activePassphrase
    ) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.appointmentRepository = appointmentRepository;
        this.rotationRepository = rotationRepository;
        this.masterSaltB64 = masterSaltB64;
        this.activePassphrase = activePassphrase;
    }

    /**
     * Rotates encrypted database fields from the old 14-word phrase to the new one.
     * This is deliberately a one-shot operation identified by old/new key fingerprints.
     * After it succeeds, restart the deployment using FIELD_CRYPTO_PASSPHRASE=new phrase.
     */
    public CryptoKeyRotationResponse rotate(CryptoKeyRotationRequest request) {
        String oldPassphrase = requireText(request.oldPassphrase(), "Old 14-word string is required");
        String newPassphrase = requireText(request.newPassphrase(), "New 14-word string is required");
        if (oldPassphrase.equals(newPassphrase)) {
            throw new IllegalArgumentException("Old and new 14-word strings must be different");
        }

        String fromFingerprint = FieldCryptoService.fingerprintFor(oldPassphrase, masterSaltB64);
        String toFingerprint = FieldCryptoService.fingerprintFor(newPassphrase, masterSaltB64);
        String activeFingerprint = FieldCryptoService.fingerprintFor(requireText(activePassphrase, "Active FIELD_CRYPTO_PASSPHRASE is not configured"), masterSaltB64);
        if (!fromFingerprint.equals(activeFingerprint)) {
            throw new IllegalArgumentException("The old 14-word string does not match the key currently used by this running backend. No rotation was started.");
        }
        String rotationId = "field-crypto:" + fromFingerprint + ":to:" + toFingerprint;

        if (rotationRepository.existsById(rotationId)) {
            throw new IllegalStateException("This key rotation has already been run or is already in progress. Refusing to run it again.");
        }

        CryptoRotationRecord record = new CryptoRotationRecord();
        record.setId(rotationId);
        record.setFromKeyFingerprint(fromFingerprint);
        record.setToKeyFingerprint(toFingerprint);
        record.setStatus("IN_PROGRESS");
        record.setStartedAt(Instant.now());
        rotationRepository.save(record);

        FieldCryptoService oldCrypto = FieldCryptoService.forPassphrase(oldPassphrase, masterSaltB64);
        FieldCryptoService newCrypto = FieldCryptoService.forPassphrase(newPassphrase, masterSaltB64);

        long usersRotated = 0;
        long officesRotated = 0;
        long appointmentsRotated = 0;
        long notesRotated = 0;

        try {
            for (AppUser user : userRepository.findAll()) {
                String displayName = oldCrypto.decryptNullable(user.getDisplayNameEncrypted());
                String telephone = oldCrypto.decryptNullable(user.getTelephoneEncrypted());
                user.setDisplayNameEncrypted(newCrypto.encryptBlankAsNull(displayName));
                user.setDisplayNameLookupHash(newCrypto.lookupHashNullable(displayName));
                user.setTelephoneEncrypted(newCrypto.encryptBlankAsNull(telephone));
                userRepository.save(user);
                usersRotated++;
            }

            for (OfficeAccount office : officeRepository.findAll()) {
                String address = oldCrypto.decryptNullable(office.getAddressEncrypted());
                String telephone = oldCrypto.decryptNullable(office.getTelephoneEncrypted());
                office.setAddressEncrypted(newCrypto.encryptBlankAsNull(address));
                office.setTelephoneEncrypted(newCrypto.encryptBlankAsNull(telephone));
                officeRepository.save(office);
                officesRotated++;
            }

            for (PatientAppointmentDocument document : appointmentRepository.findAll()) {
                String patientDisplayName = oldCrypto.decryptNullable(document.getPatientDisplayNameEncrypted());
                String patientTelephone = oldCrypto.decryptNullable(document.getPatientTelephoneEncrypted());
                String clinicName = oldCrypto.decryptNullable(document.getClinicNameEncrypted());
                String clinician = oldCrypto.decryptNullable(document.getClinicianEncrypted());
                String prescription = oldCrypto.decryptNullable(document.getPrescriptionEncrypted());

                document.setPatientDisplayNameEncrypted(newCrypto.encryptBlankAsNull(patientDisplayName));
                document.setPatientDisplayNameLookupHash(newCrypto.lookupHashNullable(patientDisplayName));
                document.setPatientTelephoneEncrypted(newCrypto.encryptBlankAsNull(patientTelephone));
                document.setClinicNameEncrypted(newCrypto.encryptBlankAsNull(clinicName));
                document.setClinicianEncrypted(newCrypto.encryptBlankAsNull(clinician));
                document.setPrescriptionEncrypted(newCrypto.encryptBlankAsNull(prescription));
                document.setPatientDisplayName(null);
                document.setClinicName(null);
                document.setClinician(null);
                document.setPrescription(null);

                for (PatientAppointmentDocument.PatientClinicalNote note : document.getNotes()) {
                    String subject = oldCrypto.decryptNullable(note.getSubjectEncrypted());
                    String noteText = oldCrypto.decryptNullable(note.getNoteTextEncrypted());
                    note.setSubjectEncrypted(newCrypto.encryptBlankAsNull(subject));
                    note.setNoteTextEncrypted(newCrypto.encryptBlankAsNull(noteText));
                    note.setSubject(null);
                    note.setNoteText(null);
                    notesRotated++;
                }

                appointmentRepository.save(document);
                appointmentsRotated++;
            }

            record.setUsersRotated(usersRotated);
            record.setOfficesRotated(officesRotated);
            record.setAppointmentsRotated(appointmentsRotated);
            record.setNotesRotated(notesRotated);
            record.setStatus("COMPLETED");
            record.setCompletedAt(Instant.now());
            record.setMessage("Restart all backend containers with FIELD_CRYPTO_PASSPHRASE set to the new 14-word string.");
            rotationRepository.save(record);
            return new CryptoKeyRotationResponse(rotationId, usersRotated, officesRotated, appointmentsRotated, notesRotated, "COMPLETED");
        } catch (RuntimeException ex) {
            record.setStatus("FAILED");
            record.setCompletedAt(Instant.now());
            record.setMessage(ex.getMessage());
            rotationRepository.save(record);
            throw ex;
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
