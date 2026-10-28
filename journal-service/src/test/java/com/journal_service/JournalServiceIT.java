package com.journal_service;

import com.journal_service.entity.Journal;
import com.journal_service.repository.JournalRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class JournalServiceIT {
    private static final String TOPIC = "user-events";
    private static final String EVENT_ID = "f9e48eae-d4a3-4f2b-8c95-b9fb359101a2";

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0.5"));

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String bootstrapServers = kafka.getBootstrapServers();
        createTopics(bootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        registry.add("app.kafka.topic.userCreated", () -> TOPIC);
        registry.add("app.kafka.topic.deadLetter", () -> "user-events-dead-letter");
        registry.add("app.jwt.secret", () -> "0123456789012345678901234567890123456789012345678901234567890123");
    }

    private static void createTopics(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 3, (short) 1),
                    new NewTopic("user-events-dead-letter", 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the Kafka test topics.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Could not create the Kafka test topics.", ex);
        }
    }

    @Autowired
    private JournalRepository journalRepository;

    @Test
    void consumeV2Event_persistsMappedJournalFromKafka() throws Exception {
        String payload = Files.readString(Path.of("..", "test-fixtures", "user-audit-event-v2.json"));
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>(TOPIC, EVENT_ID, payload)).get(10, TimeUnit.SECONDS);
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Journal journal = journalRepository.findAll().stream()
                    .filter(entry -> EVENT_ID.equals(entry.getEventId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(journal.getUserId()).isEqualTo(12L);
            assertThat(journal.getOperation()).isEqualTo("CREATE");
            assertThat(journal.getOperationTime().toString()).isEqualTo("2026-10-28T10:00:00Z");
        });
    }
}
