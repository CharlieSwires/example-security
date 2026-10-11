/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.repository;

import com.example.security.model.CryptoRotationRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CryptoRotationRecordRepository extends MongoRepository<CryptoRotationRecord, String> {
}
