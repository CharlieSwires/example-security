package com.example.security.repository;

import com.example.security.model.SecurityAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SecurityAuditEventRepository extends MongoRepository<SecurityAuditEvent, String> {
}
