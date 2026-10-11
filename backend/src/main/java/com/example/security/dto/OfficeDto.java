/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

public record OfficeDto(
        String id,
        String officeId,
        String username,
        String displayName,
        String address,
        String telephone,
        String email
) {}
