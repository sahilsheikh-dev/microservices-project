package com.user_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String name,
        @Email @Size(max = 254) String email,
        @Size(min = 8, max = 72) String password,
        @Pattern(regexp = "(?i)ADMIN|USER") String roles
) {
    public UserUpdateRequest {
        if (email != null) email = email.trim();
    }
}
