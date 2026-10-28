package com.user_service.dto;

import com.user_service.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        String roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getName(), user.getEmail(), user.getRoles(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
