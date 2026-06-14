package com.example.security.dto;

import java.time.LocalDate;

public record UpdateAppointmentAdminRequest(
        LocalDate appointmentDate,
        String appointmentTime,
        String appointmentType,
        String clinician
) {}
