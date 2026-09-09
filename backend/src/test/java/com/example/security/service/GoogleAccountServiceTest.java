package com.example.security.service;

import com.example.security.model.AppUser;
import com.example.security.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAccountServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleAccountService service = new GoogleAccountService(users);

    @Test
    void linksOnlyWhenVerifiedEmailsMatch() {
        AppUser user = user("alice", "alice@example.com", true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(users.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(users.save(user)).thenReturn(user);

        AppUser linked = service.link("alice", "google-subject", "ALICE@example.com", true);

        assertThat(linked.getGoogleSubject()).isEqualTo("google-subject");
        assertThat(linked.getGoogleLinkedAt()).isNotNull();
        verify(users).save(user);
    }

    @Test
    void rejectsDifferentEmail() {
        AppUser user = user("alice", "alice@example.com", true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.link("alice", "google-subject", "mallory@example.com", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
    }

    @Test
    void rejectsSubjectAlreadyOwnedByAnotherUser() {
        AppUser user = user("alice", "alice@example.com", true);
        AppUser other = user("bob", "bob@example.com", true);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(users.findByGoogleSubject("google-subject")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.link("alice", "google-subject", "alice@example.com", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already linked");
    }

    private AppUser user(String username, String email, boolean verified) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(verified);
        return user;
    }
}
