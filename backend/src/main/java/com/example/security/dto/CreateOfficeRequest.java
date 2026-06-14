package com.example.security.dto;

public record CreateOfficeRequest(
        String officeId,
        String username,
        String password,
        String displayName,
        String address,
        String telephone,
        String email
) {}
