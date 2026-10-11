/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import com.example.security.model.Role;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRolesRequest(@Size(max = 5) Set<Role> roles) {
}
