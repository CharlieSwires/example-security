/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.dto;

import java.util.List;

public record MfaEnableResponse(boolean enabled, List<String> recoveryCodes) {
}
