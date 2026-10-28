package com.journal_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "journals")
public class Journal {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String eventId;
    @Indexed
    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String status;
    private String comment;
    private String operation;
    @Indexed
    private Instant operationTime;
    private Instant createdAt;
    private Instant updatedAt;
}
