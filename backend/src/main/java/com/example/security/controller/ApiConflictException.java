package com.example.security.controller;

/** Expected conflict whose message is safe to return to an API client. */
public class ApiConflictException extends RuntimeException {
    public ApiConflictException(String message) {
        super(message);
    }
}
