package com.example.security.controller;

import com.example.security.dto.PatientAppointmentDocumentDto;
import com.example.security.dto.PageResponse;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import com.example.security.service.PatientAppointmentMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final int PAGE_SIZE = 50;

    @GetMapping("/appointments")
    public PageResponse<PatientAppointmentDocumentDto> myAppointments(
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication
    ) {
        return PageResponse.from(repository
                .findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(authentication.getName(), PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(mapper::toDto));
    }
}
