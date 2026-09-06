package com.example.security.dto;

import com.example.security.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 256) String password,
        @Email @Size(max = 254) String email,
        @Size(max = 5) Set<Role> roles,
        @Size(max = 64) String officeId,
        @Size(max = 200) String displayName,
        @Size(max = 40) String telephone
) {}
