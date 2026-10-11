/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateAppointmentAdminRequest(
        LocalDate appointmentDate,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String appointmentTime,
        @Size(max = 100) String appointmentType,
        @Size(max = 200) String clinician
) {}
