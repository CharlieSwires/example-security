package com.example.security.security;

import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.model.SecurityAuditEvent;
import com.example.security.repository.LoginAttemptRepository;
import com.example.security.repository.SecurityAuditEventRepository;
import com.example.security.repository.UserRepository;
import com.example.security.repository.OfficeAccountRepository;
import com.example.security.service.UserService;
import com.example.security.security.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test",
        "app.security.audit.persist=false",
        "app.security.debug-request-logging=false",
        "app.cors.allowed-origins=https://localhost:5173",
        "app.crypto.passphrase=test-only-field-crypto-passphrase",
        "app.crypto.master-salt=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "server.ssl.enabled=false"
})
@AutoConfigureMockMvc
class SecurityBehaviorTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    UserRepository userRepository;

    @MockBean
    LoginAttemptRepository loginAttemptRepository;

    @MockBean
    SecurityAuditEventRepository securityAuditEventRepository;

    @MockBean
    OfficeAccountRepository officeAccountRepository;

    @MockBean
    UserSessionService userSessionService;

    @Test
    void unauthenticatedMeReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("bob").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void officeAdminCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("dev").roles("OFFICE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void officeAdminWithoutOfficeAssignmentFailsClosed() throws Exception {
        AppUser officeAdmin = testUser("clinic-admin", "ChangeThisPassword123!", Set.of(Role.OFFICE_ADMIN));
        officeAdmin.setOfficeId(null);
        when(userRepository.findByUsername(eq("clinic-admin"))).thenReturn(Optional.of(officeAdmin));

        mockMvc.perform(get("/api/office-admin/users")
                        .with(user("clinic-admin").roles("OFFICE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void oversizedLoginFieldIsRejectedBeforeAuthentication() throws Exception {
        String username = "x".repeat(65);
        mockMvc.perform(post("/api/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientCannotRotateFieldEncryptionKeyOrSalt() throws Exception {
        mockMvc.perform(post("/api/admin/crypto/rotate")
                        .with(user("bob").roles("PATIENT"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superCanAccessAdminUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/admin/users").with(user("super").roles("SUPER")))
                .andExpect(status().isOk());
    }

    @Test
    void patientCannotAccessAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit-events").with(user("bob").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void superCanSeeEveryAuditEventField() throws Exception {
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setId("audit-123");
        event.setTimestamp(Instant.parse("2026-09-02T20:15:30Z"));
        event.setEventType("LOGIN_SUCCESS");
        event.setActor("super");
        event.setTarget("super");
        event.setClientIp("127.0.0.1");
        event.setUserAgent("test-browser");
        event.setSuccess(true);
        event.setReason("authenticated");
        event.setDetails(Map.of("session", "created"));
        when(securityAuditEventRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(event)));

        mockMvc.perform(get("/api/admin/audit-events").with(user("super").roles("SUPER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].id").value("audit-123"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].timestamp").value("2026-09-02T20:15:30Z"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].eventType").value("LOGIN_SUCCESS"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].actor").value("super"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].target").value("super"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].clientIp").value("127.0.0.1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].userAgent").value("test-browser"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].success").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].reason").value("authenticated"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].details.session").value("created"));
    }

    @Test
    void adminDeleteWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/admin/users/bob").with(user("super").roles("SUPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginCreatesSessionAndMeUsesSession() throws Exception {
        AppUser superUser = testUser("super", "ChangeThisPassword123!", Set.of(Role.SUPER));
        when(userRepository.findByUsername(eq("super"))).thenReturn(Optional.of(superUser));

        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"super\",\"password\":\"ChangeThisPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("SUPER")));
    }

    @Test
    void logoutReturns204() throws Exception {
        mockMvc.perform(post("/api/logout").with(user("super").roles("SUPER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    private AppUser testUser(String username, String password, Set<Role> roles) {
        byte[] salt = new byte[20];
        new SecureRandom(username.getBytes(StandardCharsets.UTF_8)).nextBytes(salt);

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setSalt(salt);
        user.setHash(UserService.hashPassword(salt, password));
        user.setPasswordIterations(UserService.CURRENT_PASSWORD_ITERATIONS);
        user.setRoles(roles);
        return user;
    }
}
