package com.example.security.dto;

public record PatientLookupDto(
        String username,
        String displayName,
        String telephone,
        String officeId
) {}
