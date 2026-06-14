package com.example.security.dto;

import java.time.LocalDate;

public record UpdateClinicalDocumentRequest(
        String prescription,
        LocalDate noteDate,
        String noteSubject,
        String noteText
) {}
