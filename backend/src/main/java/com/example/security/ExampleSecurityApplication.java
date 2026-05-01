package com.example.security;

import com.example.security.model.Role;
import com.example.security.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Set;

@SpringBootApplication
public class ExampleSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleSecurityApplication.class, args);
    }

    @Bean
    CommandLineRunner createInitialSuperUser(
            UserService userService,
            @Value("${app.initial-super.username}") String username,
            @Value("${app.initial-super.password}") String password
    ) {
        return args -> {
            if (userService.findByUsername(username).isEmpty()) {
                userService.createUser(username, password, Set.of(Role.SUPER));
                System.out.println("Created initial SUPER user: " + username);
            }
        };
    }
}
