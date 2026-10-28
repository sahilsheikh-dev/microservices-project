package com.journal_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.journal_service.exception.InvalidEventException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTests {
    @Test
    void kafkaErrorHandler_malformedAndInvalidEvents_areNonRetryable() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DefaultErrorHandler handler = (DefaultErrorHandler) new KafkaConsumerConfig()
                .kafkaErrorHandler(kafkaTemplate, "dead-letter");

        assertThat(handler.removeClassification(JsonProcessingException.class)).isFalse();
        assertThat(handler.removeClassification(InvalidEventException.class)).isFalse();
    }
}
