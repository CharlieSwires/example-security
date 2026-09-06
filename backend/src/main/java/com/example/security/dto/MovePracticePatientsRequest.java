package com.example.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MovePracticePatientsRequest(
        @NotBlank @Size(max = 64) String fromOfficeId,
        @NotBlank @Size(max = 64) String toOfficeId
) {}
