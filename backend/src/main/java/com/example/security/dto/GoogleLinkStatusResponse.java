package com.example.security.dto;

import java.time.Instant;

public record GoogleLinkStatusResponse(boolean available, boolean linked, String email, Instant linkedAt) {
}
