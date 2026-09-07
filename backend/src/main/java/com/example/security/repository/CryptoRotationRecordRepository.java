package com.example.security.repository;

import com.example.security.model.CryptoRotationRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CryptoRotationRecordRepository extends MongoRepository<CryptoRotationRecord, String> {
}
