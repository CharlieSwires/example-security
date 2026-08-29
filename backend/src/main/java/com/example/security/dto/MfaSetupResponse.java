package com.example.security.dto;

public record MfaSetupResponse(
        String secret,
        String otpauthUri,
        String qrCodeDataUrl
) {
}
