package com.example.security.dto;

public record OfficeDto(
        String id,
        String officeId,
        String username,
        String displayName,
        String address,
        String telephone,
        String email
) {}
