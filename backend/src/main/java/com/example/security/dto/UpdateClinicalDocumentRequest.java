package com.example.security.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateClinicalDocumentRequest(
        String prescription,
        LocalDate noteDate,
        LocalDateTime noteDateTime,
        String noteSubject,
        String noteText,
        String notePrescription
) {}
