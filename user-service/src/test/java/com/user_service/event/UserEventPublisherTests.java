package com.user_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.user_service.service.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserEventPublisherTests {
    @Test
    void publishAfterCommit_v2Fixture_sendsExactPasswordFreeJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String fixture = Files.readString(Path.of("..", "test-fixtures", "user-audit-event-v2.json"));
        UserAuditEvent event = mapper.readValue(fixture, UserAuditEvent.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("user-events"), eq("12"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        UserEventPublisher publisher = new UserEventPublisher(
                mock(ApplicationEventPublisher.class), kafkaTemplate, mapper, "user-events");

        publisher.publishAfterCommit(event);

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("user-events"), eq("12"), payload.capture());
        assertThat(mapper.readTree(payload.getValue())).isEqualTo(mapper.readTree(fixture));
        assertThat(payload.getValue()).doesNotContain("password");
    }
}
