/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

public record CryptoKeyRotationResponse(
        String rotationId,
        long usersRotated,
        long officesRotated,
        long appointmentsRotated,
        long notesRotated,
        String status
) {}
