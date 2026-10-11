/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

public record MovePracticePatientsResponse(
        String fromOfficeId,
        String toOfficeId,
        long patientsMoved,
        long cliniciansMoved,
        long appointmentsMoved,
        boolean oldOfficeDeleted
) {}
