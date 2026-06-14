package com.example.security.controller;

import com.example.security.dto.CreateOfficeRequest;
import com.example.security.dto.OfficeDto;
import com.example.security.dto.PageResponse;
import com.example.security.dto.MovePracticePatientsRequest;
import com.example.security.dto.MovePracticePatientsResponse;
import com.example.security.service.OfficeService;
import org.springframework.data.domain.PageRequest;
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

    private static final int PAGE_SIZE = 50;

    @GetMapping
    public PageResponse<OfficeDto> offices(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(officeService.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
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

