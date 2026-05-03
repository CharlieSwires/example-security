package com.example.security.security;

import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.repository.LoginAttemptRepository;
import com.example.security.repository.SecurityAuditEventRepository;
import com.example.security.repository.UserRepository;
import com.example.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
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
        "app.security.login.persistent=false",
        "app.security.debug-request-logging=false",
        "app.cors.allowed-origins=https://localhost:5173",
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

    @Test
    void unauthenticatedMeReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("dev").roles("DEVELOPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void superCanAccessAdminUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/admin/users").with(user("super").roles("SUPER")))
                .andExpect(status().isOk());
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
        user.setRoles(roles);
        return user;
    }
}
