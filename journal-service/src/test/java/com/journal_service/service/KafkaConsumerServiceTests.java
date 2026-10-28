package com.journal_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.journal_service.entity.Journal;
import com.journal_service.exception.InvalidEventException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTests {
    @Mock
    private JournalService journalService;
    private KafkaConsumerService consumer;

    @BeforeEach
    void setUp() {
        consumer = new KafkaConsumerService(journalService, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void consume_v2Event_savesMappedJournal() throws Exception {
        String json = """
                {"schemaVersion":2,"eventId":"f9e48eae-d4a3-4f2b-8c95-b9fb359101a2",
                 "occurredAt":"2026-10-28T10:00:00Z","operation":"CREATE","status":"SUCCESS",
                 "comment":"User created","userId":12,"userName":"Jane",
                 "userEmail":"jane@example.com","userRole":"USER"}
                """;
        consumer.consume(json, 1_798_000_000_000L);

        ArgumentCaptor<Journal> journal = ArgumentCaptor.forClass(Journal.class);
        verify(journalService).saveFromEvent(journal.capture());
        assertThat(journal.getValue().getEventId()).isEqualTo("f9e48eae-d4a3-4f2b-8c95-b9fb359101a2");
        assertThat(journal.getValue().getUserId()).isEqualTo(12L);
        assertThat(journal.getValue().getOperationTime()).isEqualTo(Instant.parse("2026-10-28T10:00:00Z"));
    }

    @Test
    void consume_legacyEvent_usesKafkaTimestamp() throws Exception {
        String json = """
                {"operation":"GET","status":"SUCCESS","comment":"COMPLETED",
                 "user":{"userId":12,"name":"Jane","email":"jane@example.com","roles":"USER"}}
                """;
        consumer.consume(json, 1_798_000_000_000L);

        ArgumentCaptor<Journal> journal = ArgumentCaptor.forClass(Journal.class);
        verify(journalService).saveFromEvent(journal.capture());
        assertThat(journal.getValue().getEventId()).isNull();
        assertThat(journal.getValue().getUserId()).isEqualTo(12L);
        assertThat(journal.getValue().getOperationTime()).isEqualTo(Instant.ofEpochMilli(1_798_000_000_000L));
    }

    @Test
    void consume_missingStatus_rejectsEvent() {
        assertThatThrownBy(() -> consumer.consume(
                "{\"schemaVersion\":2,\"eventId\":\"abc\",\"operation\":\"CREATE\"}", 100L))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(journalService);
    }

    @Test
    void consume_duplicateEventId_skipsDuplicate() throws Exception {
        doThrow(new DuplicateKeyException("duplicate"))
                .when(journalService).saveFromEvent(any(Journal.class));
        String json = """
                {"schemaVersion":2,"eventId":"f9e48eae-d4a3-4f2b-8c95-b9fb359101a2",
                 "occurredAt":"2026-10-28T10:00:00Z","operation":"CREATE","status":"SUCCESS",
                 "userId":12}
                """;

        consumer.consume(json, 1_798_000_000_000L);

        verify(journalService).saveFromEvent(any(Journal.class));
    }
}
