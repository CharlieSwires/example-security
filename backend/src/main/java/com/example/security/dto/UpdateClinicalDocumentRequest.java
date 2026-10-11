/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateClinicalDocumentRequest(
        @Size(max = 10000) String prescription,
        LocalDate noteDate,
        LocalDateTime noteDateTime,
        @Size(max = 200) String noteSubject,
        @Size(max = 20000) String noteText,
        @Size(max = 10000) String notePrescription
) {}
