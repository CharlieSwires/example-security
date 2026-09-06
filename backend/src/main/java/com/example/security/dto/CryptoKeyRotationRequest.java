package com.example.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CryptoKeyRotationRequest(
        @NotBlank @Size(max = 1024) String oldPassphrase,
        @NotBlank @Size(max = 1024) String newPassphrase,
        @NotBlank @Size(max = 512) String oldMasterSaltB64,
        @NotBlank @Size(max = 512) String newMasterSaltB64
) {}
