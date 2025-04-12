package com.journal_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class UserDto {
    private Long userId;
    private String name;
    private String email;
    private String roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
