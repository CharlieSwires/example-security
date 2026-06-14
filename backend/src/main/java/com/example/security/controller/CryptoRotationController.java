package com.example.security.controller;

import com.example.security.dto.CryptoKeyRotationRequest;
import com.example.security.dto.CryptoKeyRotationResponse;
import com.example.security.service.CryptoRotationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crypto")
public class CryptoRotationController {
    private final CryptoRotationService cryptoRotationService;

    public CryptoRotationController(CryptoRotationService cryptoRotationService) {
        this.cryptoRotationService = cryptoRotationService;
    }

    @PostMapping("/rotate")
    public CryptoKeyRotationResponse rotate(@RequestBody CryptoKeyRotationRequest request) {
        return cryptoRotationService.rotate(request);
    }
}
