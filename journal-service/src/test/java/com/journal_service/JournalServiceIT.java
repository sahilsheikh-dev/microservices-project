package com.journal_service;

import com.journal_service.entity.Journal;
import com.journal_service.repository.JournalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JournalServiceIT {
    private static final String TOPIC = "user-events";
    private static final String DEAD_LETTER_TOPIC = "user-events-dead-letter";
    private static final String GROUP_ID = "journal-service-it";
    private static final String EVENT_ID = "f9e48eae-d4a3-4f2b-8c95-b9fb359101a2";
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final Properties CONTAINER_IMAGES = loadContainerImages();

    static final MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse(CONTAINER_IMAGES.getProperty("mongo.image")));

    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(CONTAINER_IMAGES.getProperty("kafka.image")));

    static {
        mongo.start();
        kafka.start();
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String bootstrapServers = kafka.getBootstrapServers();
        createTopics(bootstrapServers);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP_ID);
        registry.add("app.kafka.topic.userCreated", () -> TOPIC);
        registry.add("app.kafka.topic.deadLetter", () -> DEAD_LETTER_TOPIC);
        registry.add("app.jwt.secret", () -> SECRET);
        registry.add("app.jwt.issuer", () -> "user-service");
        registry.add("SWAGGER_ENABLED", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private static void createTopics(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 3, (short) 1),
                    new NewTopic(DEAD_LETTER_TOPIC, 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the Kafka test topics.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Could not create the Kafka test topics.", ex);
        }
    }

    private static Properties loadContainerImages() {
        try (InputStream input = JournalServiceIT.class.getResourceAsStream("/testcontainers.properties")) {
            if (input == null) throw new IllegalStateException("Missing shared Testcontainers image configuration.");
            Properties images = new Properties();
            images.load(input);
            return images;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load shared Testcontainers image configuration.", ex);
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

        Properties deadLetterConsumerProperties = new Properties();
        deadLetterConsumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        deadLetterConsumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "journal-service-duplicate-it");
        deadLetterConsumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        deadLetterConsumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        deadLetterConsumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        TopicPartition deadLetterPartition = new TopicPartition(DEAD_LETTER_TOPIC, 0);

        try (KafkaConsumer<String, String> deadLetters = new KafkaConsumer<>(deadLetterConsumerProperties)) {
            deadLetters.assign(List.of(deadLetterPartition));
            deadLetters.seekToEnd(List.of(deadLetterPartition));
            deadLetters.poll(Duration.ofSeconds(1));

            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
                RecordMetadata first = producer.send(new ProducerRecord<>(TOPIC, EVENT_ID, payload))
                        .get(10, TimeUnit.SECONDS);
                RecordMetadata duplicate = producer.send(new ProducerRecord<>(TOPIC, EVENT_ID, payload))
                        .get(10, TimeUnit.SECONDS);
                assertThat(duplicate.offset()).isGreaterThan(first.offset());
                awaitConsumed(duplicate);
            }

            assertThat(deadLetters.poll(Duration.ofSeconds(1)).records(deadLetterPartition))
                    .noneMatch(record -> record.value().contains(EVENT_ID));
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Journal> matching = journalRepository.findAll().stream()
                    .filter(entry -> EVENT_ID.equals(entry.getEventId()))
                    .toList();
            assertThat(matching).hasSize(1);
            Journal journal = matching.get(0);
            assertThat(journal.getUserId()).isEqualTo(12L);
            assertThat(journal.getOperation()).isEqualTo("CREATE");
            assertThat(journal.getOperationTime().toString()).isEqualTo("2026-10-28T10:00:00Z");
        });
    }

    @Test
    void malformedKafkaPayload_isPublishedToDeadLetterTopic() throws Exception {
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "journal-service-dlt-it-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
            consumer.poll(Duration.ofSeconds(1));
            String malformed = "not-json-" + UUID.randomUUID();
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(consumerProperties))) {
                producer.send(new ProducerRecord<>(TOPIC, malformed)).get(10, TimeUnit.SECONDS);
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                boolean found = consumer.poll(Duration.ofMillis(500)).records(
                                new TopicPartition(DEAD_LETTER_TOPIC, 0)).stream()
                        .anyMatch(record -> malformed.equals(record.value()));
                assertThat(found).isTrue();
            });
        }
    }

    @Test
    void apiDocsExposeBearerJwtSecurityScheme() {
        ResponseEntity<JsonNode> docs = restTemplate.getForEntity(url("/api-docs"), JsonNode.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody().path("components").path("securitySchemes").path("bearerAuth").path("type").asText())
                .isEqualTo("http");
        assertThat(docs.getBody().path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(docs.getBody().path("components").path("securitySchemes").path("bearerAuth")
                .path("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void journalRead_isAllowedForAdminAndForbiddenForUser() {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(token("ADMIN"));
        ResponseEntity<JsonNode> admin = restTemplate.exchange(
                url("/api/journals"), HttpMethod.GET, new HttpEntity<>(adminHeaders), JsonNode.class);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(token("USER"));
        ResponseEntity<JsonNode> user = restTemplate.exchange(
                url("/api/journals"), HttpMethod.GET, new HttpEntity<>(userHeaders), JsonNode.class);
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void awaitConsumed(RecordMetadata metadata) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Properties adminProperties = new Properties();
            adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            try (AdminClient admin = AdminClient.create(adminProperties)) {
                var offsets = admin.listConsumerGroupOffsets(GROUP_ID).partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS);
                var committed = offsets.get(new TopicPartition(TOPIC, metadata.partition()));
                assertThat(committed).isNotNull();
                assertThat(committed.offset()).isGreaterThan(metadata.offset());
            }
        });
    }

    private Properties producerProperties(Properties source) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, source.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return properties;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String token(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer("user-service")
                .setSubject("1")
                .claim("email", "it-" + role.toLowerCase() + "@example.com")
                .claim("roles", List.of("ROLE_" + role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }
}
