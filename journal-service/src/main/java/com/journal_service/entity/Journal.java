package com.journal_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "journals")
public class Journal {

    @Id
    private String id;

    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String status;
    private String comment;
    private String operation;
    private LocalDateTime operationTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
