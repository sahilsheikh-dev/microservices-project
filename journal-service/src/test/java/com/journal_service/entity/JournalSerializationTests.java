package com.journal_service.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSerializationTests {
    @Test
    void serializeInstantFields_isoInstants_usesJsonStrings() throws Exception {
        Journal journal = Journal.builder()
                .operationTime(Instant.parse("2026-10-28T10:00:00Z"))
                .createdAt(Instant.parse("2026-10-28T10:01:02Z"))
                .updatedAt(Instant.parse("2026-10-28T10:03:04Z"))
                .build();
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = mapper.writeValueAsString(journal);

        assertThat(mapper.readTree(json).get("operationTime").asText()).isEqualTo("2026-10-28T10:00:00Z");
        assertThat(mapper.readTree(json).get("createdAt").asText()).isEqualTo("2026-10-28T10:01:02Z");
        assertThat(mapper.readTree(json).get("updatedAt").asText()).isEqualTo("2026-10-28T10:03:04Z");
    }
}
