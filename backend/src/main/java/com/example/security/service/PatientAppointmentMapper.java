package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.PatientAppointmentDocumentDto;
import com.example.security.dto.PatientNoteDto;
import com.example.security.model.PatientAppointmentDocument;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class PatientAppointmentMapper {
    private final FieldCryptoService crypto;

    public PatientAppointmentMapper(FieldCryptoService crypto) {
        this.crypto = crypto;
    }

    public PatientAppointmentDocumentDto toDto(PatientAppointmentDocument document) {
        List<PatientNoteDto> notes = document.getNotes()
                .stream()
                .sorted(Comparator.comparing(this::noteSortDateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(note -> new PatientNoteDto(
                        note.getCreatedDate(),
                        note.getNoteDateTime(),
                        decryptOrLegacy(note.getSubjectEncrypted(), note.getSubject()),
                        decryptOrLegacy(note.getNoteTextEncrypted(), note.getNoteText()),
                        decryptOrLegacy(note.getPrescriptionEncrypted(), note.getPrescription())
                ))
                .toList();

        return new PatientAppointmentDocumentDto(
                document.getId(),
                document.getPatientUsername(),
                decryptOrLegacy(document.getPatientDisplayNameEncrypted(), document.getPatientDisplayName()),
                crypto.decryptNullable(document.getPatientTelephoneEncrypted()),
                document.getOfficeId(),
                document.getAppointmentDate(),
                document.getAppointmentTime(),
                document.getAppointmentType(),
                decryptOrLegacy(document.getClinicNameEncrypted(), document.getClinicName()),
                decryptOrLegacy(document.getClinicianEncrypted(), document.getClinician()),
                decryptOrLegacy(document.getPrescriptionEncrypted(), document.getPrescription()),
                notes
        );
    }

    private LocalDateTime noteSortDateTime(PatientAppointmentDocument.PatientClinicalNote note) {
        if (note.getNoteDateTime() != null) {
            return note.getNoteDateTime();
        }
        LocalDate createdDate = note.getCreatedDate();
        return createdDate == null ? null : createdDate.atStartOfDay();
    }

    private String decryptOrLegacy(String encryptedValue, String legacyPlaintext) {
        String decrypted = crypto.decryptNullable(encryptedValue);
        return decrypted == null ? legacyPlaintext : decrypted;
    }
}
