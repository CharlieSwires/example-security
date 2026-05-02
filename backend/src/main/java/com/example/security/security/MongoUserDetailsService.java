package com.example.security.security;

import com.example.security.model.AppUser;
import com.example.security.service.UserService;
import com.example.security.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MongoUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final UserRepository userRepository;

    public MongoUserDetailsService(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        System.out.println();
        System.out.println("========== LOAD USER ==========");
        System.out.println("USERNAME: " + user.getUsername());
        System.out.println("ROLES FROM DB: " + user.getRoles());
        System.out.println("EMAIL: " + user.getEmail());
        System.out.println("EMAIL VERIFIED: " + user.isEmailVerified());
        System.out.println("===============================");
        System.out.println();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(UserService.encodedPasswordForSpringSecurity(user.getSalt(), user.getHash()))
                .roles(user.getRoles().stream().map(Enum::name).toArray(String[]::new))
                .build();
    }
}
