package com.example.security.repository;

import com.example.security.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailAndEmailVerifiedTrue(String email);
    Optional<AppUser> findByEmailVerificationTokenHash(String emailVerificationTokenHash);
    Optional<AppUser> findByPasswordResetTokenHash(String passwordResetTokenHash);
    Optional<AppUser> deleteByUsername(String username);
    long countByRolesContaining(com.example.security.model.Role role);
    Page<AppUser> findAllByOrderByUsernameAsc(Pageable pageable);
    List<AppUser> findByOfficeId(String officeId);
    List<AppUser> findByOfficeIdAndRolesContaining(String officeId, com.example.security.model.Role role);
}

