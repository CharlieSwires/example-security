package com.example.security.dto;

import com.example.security.model.Role;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRolesRequest(@Size(max = 5) Set<Role> roles) {
}
