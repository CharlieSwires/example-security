package com.example.security.dto;

public record MovePracticePatientsRequest(
        String fromOfficeId,
        String toOfficeId
) {}
