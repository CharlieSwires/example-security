package com.example.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ExampleController {

    @GetMapping("/patient")
    public Map<String, String> patient(Authentication authentication) {
        return Map.of(
                "screen", "Patient portal",
                "message", "PATIENT read-only ophthalmic record endpoint",
                "username", authentication.getName(),
                "access", "Read only: appointments, prescriptions, letters and patient documents"
        );
    }

    @GetMapping("/office")
    public Map<String, String> office(Authentication authentication) {
        return Map.of(
                "screen", "Office / clinicians",
                "message", "OFFICE ophthalmic clinician endpoint",
                "username", authentication.getName(),
                "access", "Clinical workflow for one office/clinic"
        );
    }

    @GetMapping("/office-admin")
    public Map<String, String> officeAdmin(Authentication authentication) {
        return Map.of(
                "screen", "Office admin",
                "message", "OFFICE_ADMIN clinic administration endpoint",
                "username", authentication.getName(),
                "access", "Clinic staff, appointments and office setup"
        );
    }

    @GetMapping("/hq")
    public Map<String, String> hq(Authentication authentication) {
        return Map.of(
                "screen", "HQ",
                "message", "HQ multi-office reporting endpoint",
                "username", authentication.getName(),
                "access", "Head-office reporting across offices"
        );
    }

    @GetMapping("/super")
    public Map<String, String> superOnly(Authentication authentication) {
        return Map.of(
                "screen", "Super admin",
                "message", "SUPER system administration endpoint",
                "username", authentication.getName(),
                "access", "System users, roles and encryption-key controls"
        );
    }

    /** Legacy compatibility endpoint. */
    @GetMapping("/user")
    public Map<String, String> user(Authentication authentication) {
        return patient(authentication);
    }

    /** Legacy compatibility endpoint. */
    @GetMapping("/developer")
    public Map<String, String> developer(Authentication authentication) {
        return officeAdmin(authentication);
    }
}
