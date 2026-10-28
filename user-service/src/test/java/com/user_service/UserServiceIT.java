package com.user_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user_service.entity.User;
import com.user_service.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceIT {
    private static final String TOPIC = "user-events";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("userdb")
            .withUsername("appuser")
            .withPassword("test-password");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.kafka.topic.userCreated", () -> TOPIC);
        registry.add("app.jwt.secret", () -> "0123456789012345678901234567890123456789012345678901234567890123");
        registry.add("app.bootstrap-admin.email", () -> "");
        registry.add("app.bootstrap-admin.password", () -> "");
    }

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    private KafkaConsumer<String, String> kafkaConsumer;

    @AfterEach
    void closeConsumer() {
        if (kafkaConsumer != null) kafkaConsumer.close(Duration.ofSeconds(2));
    }

    @Test
    void registerUser_persistsUserAndPublishesPasswordFreeV2Event() throws Exception {
        String email = "integration-" + UUID.randomUUID() + "@example.com";
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service-it-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProperties);
        List<TopicPartition> partitions = List.of(
                new TopicPartition(TOPIC, 0), new TopicPartition(TOPIC, 1), new TopicPartition(TOPIC, 2));
        kafkaConsumer.assign(partitions);
        kafkaConsumer.seekToEnd(partitions);
        kafkaConsumer.poll(Duration.ofSeconds(1));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/users",
                new HttpEntity<>("{\"name\":\"Integration User\",\"email\":\"" + email
                        + "\",\"password\":\"integration-password\"}", headers),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("roles").asText()).isEqualTo("USER");
        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches("integration-password", persisted.getPassword())).isTrue();

        JsonNode event = receiveEvent();
        assertThat(event.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(event.path("operation").asText()).isEqualTo("CREATE");
        assertThat(event.path("userEmail").asText()).isEqualTo(email);
        assertThat(event.has("password")).isFalse();
    }

    private JsonNode receiveEvent() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(500));
            for (var record : records) {
                if (record.value().contains("\"operation\":\"CREATE\"")) {
                    return objectMapper.readTree(record.value());
                }
            }
        }
        throw new AssertionError("Timed out waiting for the user audit event.");
    }
}
