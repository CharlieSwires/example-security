package com.example.security.dto;

import java.util.Set;

public record LoginResponse(
        boolean mfaRequired,
        String username,
        Set<String> roles
) {
    public static LoginResponse mfaRequired(String username) {
        return new LoginResponse(true, username, Set.of());
    }

    public static LoginResponse authenticated(String username, Set<String> roles) {
        return new LoginResponse(false, username, roles);
    }
}
