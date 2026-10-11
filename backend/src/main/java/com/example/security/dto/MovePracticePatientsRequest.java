/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MovePracticePatientsRequest(
        @NotBlank @Size(max = 64) String fromOfficeId,
        @NotBlank @Size(max = 64) String toOfficeId
) {}
