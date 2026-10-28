package com.user_service.controller;

import com.user_service.dto.PageResponse;
import com.user_service.dto.UserCreateRequest;
import com.user_service.dto.UserResponse;
import com.user_service.dto.UserUpdateRequest;
import com.user_service.entity.User;
import com.user_service.modal.CustomUserDetails;
import com.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "userId,asc") String sort) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), parseSort(sort));
        Page<User> users = userService.getAllUsers(pageable);
        return PageResponse.from(users, UserResponse::from);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return UserResponse.from(userService.getUserById(principal.getId()));
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return UserResponse.from(userService.getUserById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return UserResponse.from(userService.updateUser(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public UserResponse deleteUser(@PathVariable Long id) {
        return UserResponse.from(userService.deleteUser(id));
    }

    private Sort parseSort(String value) {
        String[] parts = value.split(",", 2);
        Sort.Direction direction = parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
        return Sort.by(direction, parts[0]);
    }
}
