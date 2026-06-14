package com.example.security.controller;

import com.example.security.dto.CreateOfficeRequest;
import com.example.security.dto.OfficeDto;
import com.example.security.dto.MovePracticePatientsRequest;
import com.example.security.dto.MovePracticePatientsResponse;
import com.example.security.service.OfficeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hq/offices")
public class OfficeManagementController {
    private final OfficeService officeService;

    public OfficeManagementController(OfficeService officeService) {
        this.officeService = officeService;
    }

    @GetMapping
    public List<OfficeDto> offices() {
        return officeService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeDto create(@RequestBody CreateOfficeRequest request) {
        return officeService.create(request);
    }

    @DeleteMapping("/{officeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String officeId) {
        officeService.deleteByOfficeId(officeId);
    }

    @PostMapping("/move-patients")
    public MovePracticePatientsResponse movePatients(@RequestBody MovePracticePatientsRequest request) {
        return officeService.movePatientsBetweenOffices(request);
    }
}

