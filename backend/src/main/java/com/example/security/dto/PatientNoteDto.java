/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientNoteDto(
        LocalDate createdDate,
        LocalDateTime noteDateTime,
        String subject,
        String noteText,
        String prescription
) {}
