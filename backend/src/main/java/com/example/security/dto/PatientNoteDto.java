package com.example.security.dto;

import java.time.LocalDate;

public record PatientNoteDto(
        LocalDate createdDate,
        String subject,
        String noteText
) {}
