package com.journal_service.dto;

import com.journal_service.entity.Journal;

import java.time.Instant;

public record JournalResponse(
        String id,
        String eventId,
        Long userId,
        String userName,
        String userEmail,
        String userRole,
        String status,
        String comment,
        String operation,
        Instant operationTime,
        Instant createdAt,
        Instant updatedAt
) {
    public static JournalResponse from(Journal journal) {
        return new JournalResponse(journal.getId(), journal.getEventId(), journal.getUserId(),
                journal.getUserName(), journal.getUserEmail(), journal.getUserRole(), journal.getStatus(),
                journal.getComment(), journal.getOperation(), journal.getOperationTime(),
                journal.getCreatedAt(), journal.getUpdatedAt());
    }
}
