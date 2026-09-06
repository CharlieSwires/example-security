package com.example.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MoveUserOfficeRequest(@NotBlank @Size(max = 64) String targetOfficeId) {}
