package com.example.security.controller;

import com.example.security.dto.CreateOfficeRequest;
import com.example.security.dto.OfficeDto;
import com.example.security.dto.PageResponse;
import com.example.security.dto.MovePracticePatientsRequest;
import com.example.security.dto.MovePracticePatientsResponse;
import com.example.security.service.OfficeService;
import com.example.security.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hq/offices")
public class OfficeManagementController {
    private final OfficeService officeService;
    private final SecurityAuditService auditService;

    public OfficeManagementController(OfficeService officeService, SecurityAuditService auditService) {
        this.officeService = officeService;
        this.auditService = auditService;
    }

    private static final int PAGE_SIZE = 50;

    @GetMapping
    public PageResponse<OfficeDto> offices(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(officeService.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeDto create(@Valid @RequestBody CreateOfficeRequest request,
                            Authentication authentication, HttpServletRequest httpRequest) {
        OfficeDto created = officeService.create(request);
        auditService.record("HQ_OFFICE_CREATED", authentication.getName(), created.officeId(), true,
                "office_created", httpRequest, Map.of("officeId", created.officeId()));
        return created;
    }

    @DeleteMapping("/{officeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String officeId, Authentication authentication,
                       HttpServletRequest httpRequest) {
        officeService.deleteByOfficeId(officeId);
        auditService.record("HQ_OFFICE_DELETED", authentication.getName(), officeId, true,
                "office_deleted", httpRequest, Map.of("officeId", officeId));
    }

    @PostMapping("/move-patients")
    public MovePracticePatientsResponse movePatients(@Valid @RequestBody MovePracticePatientsRequest request,
                                                      Authentication authentication,
                                                      HttpServletRequest httpRequest) {
        MovePracticePatientsResponse moved = officeService.movePatientsBetweenOffices(request);
        auditService.record("HQ_PRACTICE_MOVED", authentication.getName(), moved.fromOfficeId(), true,
                "patients_clinicians_appointments_moved", httpRequest,
                Map.of("fromOfficeId", moved.fromOfficeId(), "toOfficeId", moved.toOfficeId(),
                        "patientsMoved", Long.toString(moved.patientsMoved()),
                        "cliniciansMoved", Long.toString(moved.cliniciansMoved()),
                        "appointmentsMoved", Long.toString(moved.appointmentsMoved())));
        return moved;
    }
}
