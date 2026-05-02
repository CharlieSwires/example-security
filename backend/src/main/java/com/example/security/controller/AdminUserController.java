package com.example.security.controller;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.UpdateEmailRequest;
import com.example.security.dto.UpdatePasswordRequest;
import com.example.security.dto.UpdateRolesRequest;
import com.example.security.dto.UserDto;
import com.example.security.service.UserService;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> allUsers(Authentication authentication, HttpServletRequest request) {
        System.out.println();
        System.out.println("========== ADMIN USERS ==========");
        System.out.println("SESSION: " +
                (request.getSession(false) == null ? "none" : request.getSession(false).getId()));
        System.out.println("AUTH: " + authentication);
        System.out.println("AUTHORITIES: " + authentication.getAuthorities());
        System.out.println("=================================");
        System.out.println();

        return userService.findAllUsers();
    }
    @PostMapping
    public UserDto createUser(@RequestBody CreateUserRequest request) {
        return userService.toDto(userService.createUser(request.username(), request.password(), request.email(), request.roles()));
    }

    @PutMapping("/{username}/roles")
    public UserDto updateRoles(@PathVariable String username, @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(username, request.roles());
    }

    @PutMapping("/{username}/password")
    public UserDto updatePassword(@PathVariable String username, @RequestBody UpdatePasswordRequest request) {
        return userService.updatePassword(username, request.password());
    }

    @PutMapping("/{username}/email")
    public UserDto proposeEmail(@PathVariable String username, @RequestBody UpdateEmailRequest request) {
        return userService.proposeEmail(username, request.email());
    }

    @DeleteMapping("/{username}")
    public void deleteUser(@PathVariable String username) {
        userService.deleteByUsername(username);
    }
}
