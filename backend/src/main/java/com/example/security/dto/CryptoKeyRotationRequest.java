package com.example.security.dto;

public record CryptoKeyRotationRequest(
        String oldPassphrase,
        String newPassphrase
) {}
