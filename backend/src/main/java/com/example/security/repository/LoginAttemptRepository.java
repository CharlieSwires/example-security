package com.example.security.repository;

import com.example.security.model.LoginAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LoginAttemptRepository extends MongoRepository<LoginAttempt, String> {
}
