package com.example.security.dto;

public record MovePracticePatientsResponse(
        String fromOfficeId,
        String toOfficeId,
        long patientsMoved,
        long cliniciansMoved,
        long appointmentsMoved,
        boolean oldOfficeDeleted
) {}
