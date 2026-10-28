package com.user_service.event;

import java.time.Instant;
import java.util.UUID;

public record UserAuditEvent(
        int schemaVersion,
        String eventId,
        Instant occurredAt,
        String operation,
        String status,
        String comment,
        Long userId,
        String userName,
        String userEmail,
        String userRole
) {
    public static UserAuditEvent create(String operation, String status, String comment,
                                        Long userId, String userName, String userEmail, String userRole) {
        return new UserAuditEvent(2, UUID.randomUUID().toString(), Instant.now(), operation, status, comment,
                userId, userName, userEmail, userRole);
    }
}
