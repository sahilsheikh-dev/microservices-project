package com.user_service.service;

import com.user_service.dto.UserCreateRequest;
import com.user_service.dto.UserResponse;
import com.user_service.dto.UserUpdateRequest;
import com.user_service.entity.User;
import com.user_service.exception.ConflictException;
import com.user_service.exception.ResourceNotFoundException;
import com.user_service.modal.Role;
import com.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher eventPublisher;

    public UserResponse createUser(UserCreateRequest request) {
        try {
            String email = normalizeEmail(request.email());
            if (userRepository.findByEmail(email).isPresent()) {
                throw new ConflictException("An account with this email already exists.");
            }
            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .name(request.name().trim())
                    .email(email)
                    .password(passwordEncoder.encode(request.password()))
                    .roles(Role.USER.name())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            User saved = userRepository.save(user);
            eventPublisher.publish("CREATE", "SUCCESS", "User created", saved.getUserId(),
                    saved.getName(), saved.getEmail(), saved.getRoles());
            return UserResponse.from(saved);
        } catch (RuntimeException ex) {
            eventPublisher.publish("CREATE", "FAILURE", "Internal error", null,
                    null, request.email(), null);
            throw ex;
        }
    }

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    public User updateUser(Long id, UserUpdateRequest request) {
        try {
            User existing = getUserById(id);
            if (request.name() != null) existing.setName(request.name().trim());
            if (request.email() != null) {
                String email = normalizeEmail(request.email());
                userRepository.findByEmail(email)
                        .filter(found -> found.getUserId() != id)
                        .ifPresent(found -> { throw new ConflictException("An account with this email already exists."); });
                existing.setEmail(email);
            }
            if (request.password() != null) existing.setPassword(passwordEncoder.encode(request.password()));
            if (request.roles() != null) existing.setRoles(normalizeRole(request.roles()));
            existing.setUpdatedAt(LocalDateTime.now());
            User saved = userRepository.save(existing);
            eventPublisher.publish("UPDATE", "SUCCESS", "User updated", saved.getUserId(),
                    saved.getName(), saved.getEmail(), saved.getRoles());
            return saved;
        } catch (RuntimeException ex) {
            eventPublisher.publish("UPDATE", "FAILURE", "Internal error", id,
                    null, request.email(), null);
            throw ex;
        }
    }

    public User deleteUser(Long id) {
        try {
            User user = getUserById(id);
            userRepository.deleteById(id);
            eventPublisher.publish("DELETE", "SUCCESS", "User deleted", user.getUserId(),
                    user.getName(), user.getEmail(), user.getRoles());
            return user;
        } catch (RuntimeException ex) {
            eventPublisher.publish("DELETE", "FAILURE", "Internal error", id, null, null, null);
            throw ex;
        }
    }

    public User createBootstrapAdmin(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) return null;
        if (userRepository.existsByRoles(Role.ADMIN.name())) return null;
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) return null;
        LocalDateTime now = LocalDateTime.now();
        User admin = userRepository.save(User.builder()
                .name("Admin")
                .email(normalizedEmail)
                .password(passwordEncoder.encode(password))
                .roles(Role.ADMIN.name())
                .createdAt(now)
                .updatedAt(now)
                .build());
        eventPublisher.publish("CREATE", "SUCCESS", "Administrator bootstrapped", admin.getUserId(),
                admin.getName(), admin.getEmail(), admin.getRoles());
        return admin;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        boolean valid = Arrays.stream(Role.values()).anyMatch(value -> value.name().equals(normalized));
        if (!valid || normalized.contains(",")) {
            throw new IllegalArgumentException("Role must be ADMIN or USER.");
        }
        return normalized;
    }
}
