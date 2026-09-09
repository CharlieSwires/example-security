package com.example.security.service;

import com.example.security.model.AppUser;
import com.example.security.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoogleAccountService {
    private final UserRepository userRepository;

    public GoogleAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> findLinkedUser(String subject) {
        if (subject == null || subject.isBlank()) return Optional.empty();
        return userRepository.findByGoogleSubject(subject);
    }

    public AppUser link(String username, String subject, String googleEmail, boolean googleEmailVerified) {
        if (subject == null || subject.isBlank() || !googleEmailVerified) {
            throw new IllegalArgumentException("Google did not provide a verified identity");
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isEmailVerified() || user.getEmail() == null) {
            throw new IllegalArgumentException("Verify the application's email address before linking Google");
        }
        if (googleEmail == null || !user.getEmail().equals(googleEmail.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("The Google email must match the application's verified email");
        }

        Optional<AppUser> existing = userRepository.findByGoogleSubject(subject);
        if (existing.isPresent() && !existing.get().getUsername().equals(username)) {
            throw new IllegalArgumentException("This Google account is already linked to another user");
        }

        user.setGoogleSubject(subject);
        user.setGoogleLinkedAt(Instant.now());
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("This Google account is already linked to another user", ex);
        }
    }

    public void unlink(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setGoogleSubject(null);
        user.setGoogleLinkedAt(null);
        userRepository.save(user);
    }
}
