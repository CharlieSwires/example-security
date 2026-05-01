package com.example.security.controller;

import com.example.security.dto.CreateUserRequest;
import com.example.security.dto.UpdateRolesRequest;
import com.example.security.dto.UserDto;
import com.example.security.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> allUsers() {
        return userService.findAllUsers();
    }

    @PostMapping
    public UserDto createUser(@RequestBody CreateUserRequest request) {
        return userService.toDto(
                userService.createUser(request.username(), request.password(), request.roles())
        );
    }

    @PutMapping("/{username}/roles")
    public UserDto updateRoles(@PathVariable String username, @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(username, request.roles());
    }

    @DeleteMapping("/{username}")
    public void deleteUser(@PathVariable String username) {
        userService.deleteByUsername(username);
    }
}
