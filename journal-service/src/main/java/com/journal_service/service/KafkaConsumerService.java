package com.journal_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journal_service.entity.Journal;
import com.journal_service.exception.InvalidEventException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final JournalService journalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topic.userCreated}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message, @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long recordTimestamp)
            throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(message);
        if (root == null || !root.isObject()) {
            throw new InvalidEventException("Event must be a JSON object.");
        }

        boolean legacy = root.has("user") && !root.has("eventId");
        if (!legacy && (!root.has("schemaVersion") || root.path("schemaVersion").asInt(-1) != 2)) {
            throw new InvalidEventException("Unsupported event schema version.");
        }
        String operation = requiredText(root, "operation");
        String status = requiredText(root, "status");
        Set<String> validOperations = legacy
                ? Set.of("CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT", "GET")
                : Set.of("CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT");
        if (!validOperations.contains(operation) || !Set.of("SUCCESS", "FAILURE").contains(status)) {
            throw new InvalidEventException("Event has an unsupported operation or status.");
        }
        JsonNode user = legacy ? root.path("user") : root;

        Instant occurredAt = readOccurredAt(root, recordTimestamp);
        JsonNode eventIdNode = root.get("eventId");
        String eventId = eventIdNode == null || eventIdNode.isNull() ? null : requireText(eventIdNode, "eventId");
        if (!legacy) {
            if (eventId == null) throw new InvalidEventException("Event is missing eventId.");
            try {
                UUID.fromString(eventId);
            } catch (IllegalArgumentException ex) {
                throw new InvalidEventException("Event has invalid eventId.", ex);
            }
        }
        Long userId = nullableLong(user.get("userId"));

        Journal journal = Journal.builder()
                .eventId(eventId)
                .userId(userId)
                .userName(textOrNull(user.get(legacy ? "name" : "userName")))
                .userEmail(textOrNull(user.get(legacy ? "email" : "userEmail")))
                .userRole(textOrNull(user.get(legacy ? "roles" : "userRole")))
                .operation(operation)
                .status(status)
                .comment(textOrNull(root.get("comment")))
                .operationTime(occurredAt)
                .build();

        try {
            journalService.saveFromEvent(journal);
            log.info("Saved journal event {}", eventId == null ? "legacy" : eventId);
        } catch (DuplicateKeyException ex) {
            if (eventId == null) throw ex;
            log.info("Skipped duplicate journal event {}", eventId);
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = textOrNull(root.get(field));
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Event is missing " + field + ".");
        }
        return value;
    }

    private String requireText(JsonNode node, String field) {
        String value = textOrNull(node);
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Event has invalid " + field + ".");
        }
        return value;
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.isTextual() ? node.asText() : null;
    }

    private Long nullableLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new InvalidEventException("Event has invalid userId.");
        }
        return node.longValue();
    }

    private Instant readOccurredAt(JsonNode root, long recordTimestamp) {
        JsonNode node = root.get("occurredAt");
        if (node == null || node.isNull()) {
            if (recordTimestamp < 0) throw new InvalidEventException("Kafka record has no timestamp.");
            return Instant.ofEpochMilli(recordTimestamp);
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new InvalidEventException("Event has invalid occurredAt.");
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException ex) {
            throw new InvalidEventException("Event has invalid occurredAt.", ex);
        }
    }
}
