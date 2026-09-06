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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final int batchSize;

    public CryptoRotationService(
            UserRepository userRepository,
            OfficeAccountRepository officeRepository,
            PatientAppointmentDocumentRepository appointmentRepository,
            CryptoRotationRecordRepository rotationRepository,
            @Value("${app.crypto.master-salt:}") String masterSaltB64,
            @Value("${app.crypto.passphrase:}") String activePassphrase,
            @Value("${app.crypto.rotation-batch-size:100}") int batchSize
    ) {
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.appointmentRepository = appointmentRepository;
        this.rotationRepository = rotationRepository;
        this.masterSaltB64 = masterSaltB64;
        this.activePassphrase = activePassphrase;
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("app.crypto.rotation-batch-size must be between 1 and 1000");
        }
        this.batchSize = batchSize;
    }

    /**
     * Rotates encrypted database fields from the old passphrase/salt pair to the new pair.
     * This is deliberately a one-shot operation identified by old/new key fingerprints.
     * After it succeeds, restart the deployment using both new FIELD_CRYPTO values.
     */
    public CryptoKeyRotationResponse rotate(CryptoKeyRotationRequest request) {
        String oldPassphrase = requireText(request.oldPassphrase(), "Old 14-word string is required");
        String newPassphrase = requireText(request.newPassphrase(), "New 14-word string is required");
        String oldMasterSaltB64 = requireText(request.oldMasterSaltB64(), "Old master salt is required");
        String newMasterSaltB64 = FieldCryptoService.requireNewMasterSalt(
                requireText(request.newMasterSaltB64(), "New master salt is required"));

        String fromFingerprint = FieldCryptoService.fingerprintFor(oldPassphrase, oldMasterSaltB64);
        String toFingerprint = FieldCryptoService.fingerprintFor(newPassphrase, newMasterSaltB64);
        String activeFingerprint = FieldCryptoService.fingerprintFor(requireText(activePassphrase, "Active FIELD_CRYPTO_PASSPHRASE is not configured"), masterSaltB64);
        if (!fromFingerprint.equals(activeFingerprint)) {
            throw new IllegalArgumentException("The old passphrase and master salt do not match the key currently used by this running backend. No rotation was started.");
        }
        if (fromFingerprint.equals(toFingerprint)) {
            throw new IllegalArgumentException("The new passphrase/master-salt pair must be different from the current pair");
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

        FieldCryptoService oldCrypto = FieldCryptoService.forPassphrase(oldPassphrase, oldMasterSaltB64);
        FieldCryptoService newCrypto = FieldCryptoService.forPassphrase(newPassphrase, newMasterSaltB64);

        long usersRotated = 0;
        long officesRotated = 0;
        long appointmentsRotated = 0;
        long notesRotated = 0;

        try {
            usersRotated = rotateUsers(oldCrypto, newCrypto);
            officesRotated = rotateOffices(oldCrypto, newCrypto);
            AppointmentRotationCounts appointmentCounts = rotateAppointments(oldCrypto, newCrypto);
            appointmentsRotated = appointmentCounts.appointments();
            notesRotated = appointmentCounts.notes();

            record.setUsersRotated(usersRotated);
            record.setOfficesRotated(officesRotated);
            record.setAppointmentsRotated(appointmentsRotated);
            record.setNotesRotated(notesRotated);
            record.setStatus("COMPLETED");
            record.setCompletedAt(Instant.now());
            record.setMessage("Restart all backend containers with both FIELD_CRYPTO_PASSPHRASE and FIELD_CRYPTO_MASTER_SALT_B64 set to their new values.");
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

    private long rotateUsers(FieldCryptoService oldCrypto, FieldCryptoService newCrypto) {
        long rotated = 0;
        int pageNumber = 0;
        Page<AppUser> page;
        do {
            page = userRepository.findAll(PageRequest.of(pageNumber++, batchSize, Sort.by("id").ascending()));
            for (AppUser user : page.getContent()) {
                String displayName = oldCrypto.decryptNullable(user.getDisplayNameEncrypted());
                String telephone = oldCrypto.decryptNullable(user.getTelephoneEncrypted());
                String totpSecret = oldCrypto.decryptNullable(user.getTotpSecretEncrypted());
                user.setDisplayNameEncrypted(newCrypto.encryptBlankAsNull(displayName));
                user.setDisplayNameLookupHash(newCrypto.lookupHashNullable(displayName));
                user.setTelephoneEncrypted(newCrypto.encryptBlankAsNull(telephone));
                user.setTotpSecretEncrypted(newCrypto.encryptBlankAsNull(totpSecret));
            }
            userRepository.saveAll(page.getContent());
            rotated += page.getNumberOfElements();
        } while (page.hasNext());
        return rotated;
    }

    private long rotateOffices(FieldCryptoService oldCrypto, FieldCryptoService newCrypto) {
        long rotated = 0;
        int pageNumber = 0;
        Page<OfficeAccount> page;
        do {
            page = officeRepository.findAll(PageRequest.of(pageNumber++, batchSize, Sort.by("id").ascending()));
            for (OfficeAccount office : page.getContent()) {
                String address = oldCrypto.decryptNullable(office.getAddressEncrypted());
                String telephone = oldCrypto.decryptNullable(office.getTelephoneEncrypted());
                office.setAddressEncrypted(newCrypto.encryptBlankAsNull(address));
                office.setTelephoneEncrypted(newCrypto.encryptBlankAsNull(telephone));
            }
            officeRepository.saveAll(page.getContent());
            rotated += page.getNumberOfElements();
        } while (page.hasNext());
        return rotated;
    }

    private AppointmentRotationCounts rotateAppointments(FieldCryptoService oldCrypto, FieldCryptoService newCrypto) {
        long appointments = 0;
        long notes = 0;
        int pageNumber = 0;
        Page<PatientAppointmentDocument> page;
        do {
            page = appointmentRepository.findAll(PageRequest.of(pageNumber++, batchSize, Sort.by("id").ascending()));
            for (PatientAppointmentDocument document : page.getContent()) {
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

                if (document.getNotes() != null) {
                    for (PatientAppointmentDocument.PatientClinicalNote note : document.getNotes()) {
                        String subject = oldCrypto.decryptNullable(note.getSubjectEncrypted());
                        String noteText = oldCrypto.decryptNullable(note.getNoteTextEncrypted());
                        String notePrescription = oldCrypto.decryptNullable(note.getPrescriptionEncrypted());
                        note.setSubjectEncrypted(newCrypto.encryptBlankAsNull(subject));
                        note.setNoteTextEncrypted(newCrypto.encryptBlankAsNull(noteText));
                        note.setPrescriptionEncrypted(newCrypto.encryptBlankAsNull(notePrescription));
                        note.setSubject(null);
                        note.setNoteText(null);
                        note.setPrescription(null);
                        notes++;
                    }
                }
            }
            appointmentRepository.saveAll(page.getContent());
            appointments += page.getNumberOfElements();
        } while (page.hasNext());
        return new AppointmentRotationCounts(appointments, notes);
    }

    private record AppointmentRotationCounts(long appointments, long notes) { }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
