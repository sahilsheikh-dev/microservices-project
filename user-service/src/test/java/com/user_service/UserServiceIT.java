package com.user_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user_service.entity.User;
import com.user_service.repository.UserRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
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
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceIT {
    private static final String TOPIC = "user-events";
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final Properties CONTAINER_IMAGES = loadContainerImages();

    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse(
            CONTAINER_IMAGES.getProperty("mysql.image")))
            .withDatabaseName("userdb")
            .withUsername("appuser")
            .withPassword("test-password");

    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(
            CONTAINER_IMAGES.getProperty("kafka.image")));

    static {
        mysql.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String bootstrapServers = kafka.getBootstrapServers();
        createTopic(bootstrapServers);
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        registry.add("app.kafka.topic.userCreated", () -> TOPIC);
        registry.add("app.jwt.secret", () -> SECRET);
        registry.add("app.bootstrap-admin.email", () -> "");
        registry.add("app.bootstrap-admin.password", () -> "");
        registry.add("SWAGGER_ENABLED", () -> "true");
    }

    private static Properties loadContainerImages() {
        try (InputStream input = UserServiceIT.class.getResourceAsStream("/testcontainers.properties")) {
            if (input == null) throw new IllegalStateException("Missing shared Testcontainers image configuration.");
            Properties images = new Properties();
            images.load(input);
            return images;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load shared Testcontainers image configuration.", ex);
        }
    }

    private static void createTopic(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the Kafka test topic.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Could not create the Kafka test topic.", ex);
        }
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
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
                        + "\",\"password\":\"integration-password\",\"roles\":\"ADMIN\"}", headers),
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

    @Test
    void logout_requiresAuthentication() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void apiDocsExposeBearerJwtSecurityScheme() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api-docs", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode bearer = response.getBody().path("components").path("securitySchemes").path("bearerAuth");
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void loginWithWrongPassword_returnsUnauthorized() throws IOException, InterruptedException {
        String email = registerUser();
        assertThat(login(email, "wrong-password").statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void loginIsRateLimitedAfterFiveWrongPasswords() throws IOException, InterruptedException {
        String email = registerUser();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login(email, "wrong-password").statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
        assertThat(login(email, "wrong-password").statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private String registerUser() {
        String email = "integration-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/users",
                new HttpEntity<>("{\"name\":\"Integration User\",\"email\":\"" + email
                        + "\",\"password\":\"integration-password\"}", jsonHeaders()),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return email;
    }

    private HttpResponse<String> login(String email, String password) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
