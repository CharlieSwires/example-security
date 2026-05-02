package com.example.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ExampleController {

    @GetMapping("/user")
    public Map<String, String> user(Authentication authentication) {
        return Map.of(
                "message", "Hello USER endpoint",
                "username", authentication.getName()
        );
    }

    @GetMapping("/developer")
    public Map<String, String> developer(Authentication authentication) {
        return Map.of(
                "message", "Hello DEVELOPER endpoint",
                "username", authentication.getName()
        );
    }
}
