package com.example.security.controller;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.UpdateEmailRequest;
import com.example.security.dto.UpdatePasswordRequest;
import com.example.security.dto.UpdateRolesRequest;
import com.example.security.dto.UserDto;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;
    private final SecurityAuditService auditService;

    public AdminUserController(UserService userService, SecurityAuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<UserDto> allUsers() {
        return userService.findAllUsers();
    }

    @PostMapping
    public UserDto createUser(@RequestBody CreateUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        UserDto created = userService.toDto(userService.createUser(request.username(), request.password(), request.email(), request.roles()));
        auditService.record("USER_CREATED", authentication.getName(), created.username(), true, "admin_created_user", httpRequest,
                Map.of("roles", String.valueOf(created.roles())));
        return created;
    }

    @PutMapping("/{username}/roles")
    public UserDto updateRoles(@PathVariable String username, @RequestBody UpdateRolesRequest request,
                               Authentication authentication, HttpServletRequest httpRequest) {
        UserDto updated = userService.updateRoles(username, request.roles());
        auditService.record("USER_ROLES_CHANGED", authentication.getName(), username, true, "admin_changed_roles", httpRequest,
                Map.of("roles", String.valueOf(updated.roles())));
        return updated;
    }

    @PutMapping("/{username}/password")
    public UserDto updatePassword(@PathVariable String username, @RequestBody UpdatePasswordRequest request,
                                  Authentication authentication, HttpServletRequest httpRequest) {
        UserDto updated = userService.updatePassword(username, request.password());
        auditService.record("USER_PASSWORD_CHANGED_BY_ADMIN", authentication.getName(), username, true, "admin_changed_password", httpRequest);
        return updated;
    }

    @PutMapping("/{username}/email")
    public UserDto proposeEmail(@PathVariable String username, @RequestBody UpdateEmailRequest request,
                                Authentication authentication, HttpServletRequest httpRequest) {
        UserDto updated = userService.proposeEmail(username, request.email());
        auditService.record("EMAIL_VERIFICATION_REQUESTED", authentication.getName(), username, true, "admin_proposed_email", httpRequest);
        return updated;
    }

    @DeleteMapping("/{username}")
    public void deleteUser(@PathVariable String username, Authentication authentication, HttpServletRequest httpRequest) {
        userService.deleteByUsername(username);
        auditService.record("USER_DELETED", authentication.getName(), username, true, "admin_deleted_user", httpRequest);
    }
}
