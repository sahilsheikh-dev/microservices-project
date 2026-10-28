package com.user_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user_service.event.UserAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private final ApplicationEventPublisher applicationEvents;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public UserEventPublisher(ApplicationEventPublisher applicationEvents,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.kafka.topic.userCreated}") String topic) {
        this.applicationEvents = applicationEvents;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(String operation, String status, String comment, Long userId,
                        String userName, String userEmail, String userRole) {
        applicationEvents.publishEvent(UserAuditEvent.create(
                operation, status, comment, userId, userName, userEmail, userRole));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishAfterCommit(UserAuditEvent event) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize user event {} for topic {}", event.eventId(), topic, ex);
            return;
        }

        String key = event.userId() == null ? "unknown" : event.userId().toString();
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Failed to deliver user event {} to topic {}", event.eventId(), topic);
                }
            });
        } catch (RuntimeException ex) {
            log.error("Failed to deliver user event {} to topic {}", event.eventId(), topic);
        }
    }
}
