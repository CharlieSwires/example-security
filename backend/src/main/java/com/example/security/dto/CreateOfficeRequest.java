package com.example.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOfficeRequest(
        @NotBlank @Size(max = 64) String officeId,
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 256) String password,
        @Size(max = 200) String displayName,
        @Size(max = 1000) String address,
        @Size(max = 40) String telephone,
        @Email @Size(max = 254) String email
) {}
