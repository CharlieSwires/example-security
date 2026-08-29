package com.example.security.dto;

import java.util.List;

public record MfaEnableResponse(boolean enabled, List<String> recoveryCodes) {
}
