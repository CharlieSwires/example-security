package com.example.security.service;

import com.example.security.dto.UserDto;
import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.repository.UserRepository;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {

    private static final int SALT_BYTES = 20;
    private static final int ITERATIONS = 65_000;
    private static final int KEY_LENGTH_BITS = 256;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser createUser(String username, String password, Set<Role> roles) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        byte[] salt = newSalt();

        AppUser user = new AppUser();
        user.setUsername(username.trim());
        user.setSalt(salt);
        user.setHash(hashPassword(salt, password));
        user.setRoles(roles == null || roles.isEmpty() ? Set.of(Role.USER) : roles);

        return userRepository.save(user);
    }

    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<UserDto> findAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UserDto updateRoles(String username, Set<Role> roles) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        user.setRoles(roles == null || roles.isEmpty() ? Set.of(Role.USER) : roles);
        return toDto(userRepository.save(user));
    }

    public UserDto updatePassword(String username, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        byte[] salt = newSalt();
        user.setSalt(salt);
        user.setHash(hashPassword(salt, newPassword));

        return toDto(userRepository.save(user));
    }

    public void deleteByUsername(String username) {
        userRepository.deleteByUsername(username);
    }

    public UserDto toDto(AppUser user) {
        return new UserDto(user.getId(), user.getUsername(), user.getRoles());
    }

    private byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }

    public static String hashPassword(byte[] salt, String password) {
        try {
            Base64.Encoder encoder = Base64.getEncoder();
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "BC");
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKey secretKey = factory.generateSecret(keySpec);
            return encoder.encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash password", e);
        }
    }

    public static String encodedPasswordForSpringSecurity(byte[] salt, String hash) {
        return Base64.getEncoder().encodeToString(salt) + ":" + hash;
    }
}
