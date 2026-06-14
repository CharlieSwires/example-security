package com.example.security.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientAppointmentDocumentDto(
        String id,
        String patientUsername,
        String patientDisplayName,
        String patientTelephone,
        String officeId,
        LocalDate appointmentDate,
        String appointmentTime,
        String appointmentType,
        String clinicName,
        String clinician,
        String prescription,
        List<PatientNoteDto> notes
) {}
