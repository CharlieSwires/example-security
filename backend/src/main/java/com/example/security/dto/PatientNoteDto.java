package com.example.security.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientNoteDto(
        LocalDate createdDate,
        LocalDateTime noteDateTime,
        String subject,
        String noteText,
        String prescription
) {}
