package com.example.security.dto;

public record ResetPasswordRequest(String token, String password) {}
