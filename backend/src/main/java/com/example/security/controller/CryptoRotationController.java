package com.example.security.controller;

import com.example.security.dto.CryptoKeyRotationRequest;
import com.example.security.dto.CryptoKeyRotationResponse;
import com.example.security.service.CryptoRotationService;
import com.example.security.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crypto")
public class CryptoRotationController {
    private final CryptoRotationService cryptoRotationService;
    private final SecurityAuditService auditService;

    public CryptoRotationController(CryptoRotationService cryptoRotationService,
                                    SecurityAuditService auditService) {
        this.cryptoRotationService = cryptoRotationService;
        this.auditService = auditService;
    }

    @PostMapping("/rotate")
    @PreAuthorize("hasRole('SUPER')")
    public CryptoKeyRotationResponse rotate(@Valid @RequestBody CryptoKeyRotationRequest request,
                                            Authentication authentication,
                                            HttpServletRequest httpRequest) {
        try {
            CryptoKeyRotationResponse response = cryptoRotationService.rotate(request);
            auditService.record("FIELD_CRYPTO_ROTATED", authentication.getName(), response.rotationId(),
                    true, "rotation_completed", httpRequest);
            return response;
        } catch (RuntimeException ex) {
            auditService.record("FIELD_CRYPTO_ROTATION_FAILED", authentication.getName(), null,
                    false, "rotation_rejected_or_failed", httpRequest);
            throw ex;
        }
    }
}
