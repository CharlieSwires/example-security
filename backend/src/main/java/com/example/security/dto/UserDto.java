package com.example.security.dto;

import com.example.security.model.Role;

import java.util.Set;

public record UserDto(String id, String username, Set<Role> roles) {
}
