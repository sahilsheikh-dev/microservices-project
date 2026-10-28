package com.user_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuditEventTests {
    @Test
    void create_serializesV2ContractWithoutPassword() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        UserAuditEvent event = UserAuditEvent.create("CREATE", "SUCCESS", "User created",
                7L, "Jane", "jane@example.com", "USER");

        String json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"schemaVersion\":2", "\"operation\":\"CREATE\"",
                "\"status\":\"SUCCESS\"", "\"userId\":7", "\"userRole\":\"USER\"");
        assertThat(json).containsPattern("\"eventId\":\"[0-9a-f-]{36}\"")
                .containsPattern("\"occurredAt\":\"\\d{4}-\\d{2}-\\d{2}T[^\\\"]+Z\"");
        assertThat(json).doesNotContain("password");
    }
}
