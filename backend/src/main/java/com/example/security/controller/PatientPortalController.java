package com.example.security.controller;

import com.example.security.dto.PatientAppointmentDocumentDto;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import com.example.security.service.PatientAppointmentMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientPortalController {
    private final PatientAppointmentDocumentRepository repository;
    private final PatientAppointmentMapper mapper;

    public PatientPortalController(PatientAppointmentDocumentRepository repository, PatientAppointmentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @GetMapping("/appointments")
    public List<PatientAppointmentDocumentDto> myAppointments(Authentication authentication) {
        return repository
                .findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(authentication.getName())
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
