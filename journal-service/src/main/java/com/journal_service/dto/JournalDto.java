package com.journal_service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JournalDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String serviceName;
    private String status; // SUCCESS or FAILURE
    private String comment; // COMPLETED or error message
    private String operation;
    private LocalDateTime operationTime;
}
