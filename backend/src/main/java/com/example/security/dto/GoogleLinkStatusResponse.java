/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import java.time.Instant;

public record GoogleLinkStatusResponse(boolean available, boolean linked, String email, Instant linkedAt) {
}
