/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import com.example.security.model.Role;
import java.util.Set;

public record UserDto(
        String id,
        String username,
        String email,
        boolean emailVerified,
        String pendingEmail,
        Set<Role> roles,
        String officeId,
        String displayName,
        String telephone
) {}
