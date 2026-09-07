package com.example.security.dto;

public record CryptoKeyRotationResponse(
        String rotationId,
        long usersRotated,
        long officesRotated,
        long appointmentsRotated,
        long notesRotated,
        String status
) {}
