package com.example.security.dto;

import java.util.Set;

public record LoginResponse(
        boolean mfaRequired,
        String username,
        Set<String> roles,
        String officeId
) {
    public static LoginResponse mfaRequired(String username) {
        return new LoginResponse(true, username, Set.of(), null);
    }

    public static LoginResponse authenticated(String username, Set<String> roles, String officeId) {
        return new LoginResponse(false, username, roles, officeId);
    }
}
