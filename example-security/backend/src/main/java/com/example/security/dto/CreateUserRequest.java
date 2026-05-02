package com.example.security.dto;

import com.example.security.model.Role;
import java.util.Set;

public record CreateUserRequest(String username, String password, String email, Set<Role> roles) {}
