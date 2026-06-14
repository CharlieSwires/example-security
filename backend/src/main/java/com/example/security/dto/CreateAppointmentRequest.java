package com.example.security.dto;

import java.time.LocalDate;

public record CreateAppointmentRequest(
        String patientUsername,
        String patientDisplayName,
        String patientTelephone,
        String officeId,
        LocalDate appointmentDate,
        String appointmentTime,
        String appointmentType,
        String clinicName,
        String clinician
) {}
