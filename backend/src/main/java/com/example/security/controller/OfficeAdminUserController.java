package com.example.security.controller;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.PageResponse;
import com.example.security.dto.UpdateEmailRequest;
import com.example.security.dto.UpdatePasswordRequest;
import com.example.security.dto.UpdateRolesRequest;
import com.example.security.dto.UserDto;
import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.repository.UserRepository;
import com.example.security.security.SecurityAuditService;
import com.example.security.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/office-admin/users")
public class OfficeAdminUserController {
    private static final int PAGE_SIZE = 50;
    private static final String DEFAULT_OFFICE_ID = "goole";
    private static final Set<Role> OFFICE_ADMIN_ALLOWED_ROLES = Set.of(Role.PATIENT, Role.OFFICE, Role.OFFICE_ADMIN);

    private final UserService userService;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;

    public OfficeAdminUserController(UserService userService, UserRepository userRepository, SecurityAuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<UserDto> officeUsers(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(required = false) String officeId,
                                             Authentication authentication) {
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId);
        return PageResponse.from(userRepository.findByOfficeIdOrderByUsernameAsc(chosenOfficeId, PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(userService::toDto));
    }

    @PostMapping
    public UserDto createUser(@RequestBody CreateUserRequest request,
                              @RequestParam(required = false) String officeId,
                              Authentication authentication,
                              HttpServletRequest httpRequest) {
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId == null ? request.officeId() : officeId);
        Set<Role> roles = allowedRolesOnly(request.roles());
        UserDto created = userService.toDto(userService.createUser(request.username(), request.password(), request.email(), roles,
                chosenOfficeId, request.displayName(), request.telephone()));
        auditService.record("OFFICE_ADMIN_USER_CREATED", authentication.getName(), created.username(), true,
                "office_admin_created_user", httpRequest, Map.of("officeId", chosenOfficeId, "roles", String.valueOf(created.roles())));
        return created;
    }

    @PutMapping("/{username}/roles")
    public UserDto updateRoles(@PathVariable String username,
                               @RequestBody UpdateRolesRequest request,
                               @RequestParam(required = false) String officeId,
                               Authentication authentication,
                               HttpServletRequest httpRequest) {
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId);
        ensureManagedUser(username, chosenOfficeId);
        UserDto updated = userService.updateRoles(username, allowedRolesOnly(request.roles()));
        auditService.record("OFFICE_ADMIN_USER_ROLES_CHANGED", authentication.getName(), username, true,
                "office_admin_changed_roles", httpRequest, Map.of("officeId", chosenOfficeId, "roles", String.valueOf(updated.roles())));
        return updated;
    }

    @PutMapping("/{username}/password")
    public UserDto updatePassword(@PathVariable String username,
                                  @RequestBody UpdatePasswordRequest request,
                                  @RequestParam(required = false) String officeId,
                                  Authentication authentication,
                                  HttpServletRequest httpRequest) {
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId);
        ensureManagedUser(username, chosenOfficeId);
        UserDto updated = userService.updatePassword(username, request.password());
        auditService.record("OFFICE_ADMIN_USER_PASSWORD_CHANGED", authentication.getName(), username, true,
                "office_admin_changed_password", httpRequest, Map.of("officeId", chosenOfficeId));
        return updated;
    }

    @PutMapping("/{username}/email")
    public UserDto proposeEmail(@PathVariable String username,
                                @RequestBody UpdateEmailRequest request,
                                @RequestParam(required = false) String officeId,
                                Authentication authentication,
                                HttpServletRequest httpRequest) {
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId);
        ensureManagedUser(username, chosenOfficeId);
        UserDto updated = userService.proposeEmail(username, request.email());
        auditService.record("OFFICE_ADMIN_EMAIL_VERIFICATION_REQUESTED", authentication.getName(), username, true,
                "office_admin_proposed_email", httpRequest, Map.of("officeId", chosenOfficeId));
        return updated;
    }

    @DeleteMapping("/{username}")
    public void deleteUser(@PathVariable String username,
                           @RequestParam(required = false) String officeId,
                           Authentication authentication,
                           HttpServletRequest httpRequest) {
        if (username.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account here");
        }
        AppUser current = currentUser(authentication);
        String chosenOfficeId = chooseOfficeId(current, officeId);
        ensureManagedUser(username, chosenOfficeId);
        userService.deleteByUsername(username);
        auditService.record("OFFICE_ADMIN_USER_DELETED", authentication.getName(), username, true,
                "office_admin_deleted_user", httpRequest, Map.of("officeId", chosenOfficeId));
    }

    private AppUser currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
    }

    private String chooseOfficeId(AppUser current, String requestedOfficeId) {
        if (isHqOrSuper(current)) {
            String chosen = normalizeOfficeId(requestedOfficeId);
            return chosen == null ? DEFAULT_OFFICE_ID : chosen;
        }
        return requiredOfficeId(current);
    }

    private String requiredOfficeId(AppUser current) {
        String officeId = normalizeOfficeId(current.getOfficeId());
        return officeId == null ? DEFAULT_OFFICE_ID : officeId;
    }

    private boolean isHqOrSuper(AppUser current) {
        Set<Role> roles = current.getRoles() == null ? Set.of() : current.getRoles();
        return roles.contains(Role.HQ) || roles.contains(Role.SUPER);
    }

    private String normalizeOfficeId(String officeId) {
        return officeId == null || officeId.isBlank() ? null : officeId.trim().toLowerCase();
    }

    private Set<Role> allowedRolesOnly(Set<Role> requestedRoles) {
        Set<Role> roles = requestedRoles == null || requestedRoles.isEmpty()
                ? Set.of(Role.PATIENT)
                : requestedRoles.stream().map(Role::normalized).collect(Collectors.toUnmodifiableSet());
        if (!OFFICE_ADMIN_ALLOWED_ROLES.containsAll(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OFFICE_ADMIN can only assign PATIENT, OFFICE and OFFICE_ADMIN roles");
        }
        return roles;
    }

    private AppUser ensureManagedUser(String username, String chosenOfficeId) {
        AppUser target = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        String targetOfficeId = normalizeOfficeId(target.getOfficeId());
        if (!chosenOfficeId.equals(targetOfficeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User belongs to another office");
        }
        Set<Role> targetRoles = target.getRoles() == null ? Set.of(Role.PATIENT) : target.getRoles().stream().map(Role::normalized).collect(Collectors.toSet());
        if (!OFFICE_ADMIN_ALLOWED_ROLES.containsAll(targetRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OFFICE_ADMIN cannot manage HQ or SUPER users");
        }
        return target;
    }
}
