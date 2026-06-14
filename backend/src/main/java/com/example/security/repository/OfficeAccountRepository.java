package com.example.security.repository;

import com.example.security.model.OfficeAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeAccountRepository extends MongoRepository<OfficeAccount, String> {
    Optional<OfficeAccount> findByOfficeId(String officeId);
    Optional<OfficeAccount> findByUsername(String username);
    List<OfficeAccount> findAllByOrderByOfficeIdAsc();
    Page<OfficeAccount> findAllByOrderByOfficeIdAsc(Pageable pageable);
    void deleteByOfficeId(String officeId);
    boolean existsByOfficeId(String officeId);
    boolean existsByUsername(String username);
}
