package com.example.security.controller;

import com.example.security.dto.PageResponse;
import com.example.security.dto.SecurityAuditEventDto;
import com.example.security.repository.SecurityAuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-events")
public class AdminAuditController {
    private static final int PAGE_SIZE = 50;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("timestamp"),
            Sort.Order.desc("id")
    );

    private final SecurityAuditEventRepository repository;

    public AdminAuditController(SecurityAuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<SecurityAuditEventDto> auditEvents(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(repository
                .findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE, NEWEST_FIRST))
                .map(SecurityAuditEventDto::from));
    }
}
