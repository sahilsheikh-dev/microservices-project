# Microservices Project: User Service and Journal Service

Two Spring Boot services exchange user-operation events through Kafka. The user service stores accounts in MySQL; the journal service consumes those events, stores journal records in MongoDB, and exposes a REST API.

## Architecture

```text
  +------------------+       +-----------------------+       +-----------+
  | Client / Postman | -----> | user-service         | ----> | MySQL 8   |
  +--------+---------+  REST  | REST API, JWT        |       | userdb    |
           |                  | Kafka producer       |       +-----------+
           |                  +-----------+----------+
           |                              |
           |                              v
           |                  +-----------------------+
           |                  | Kafka 7.5.1 (KRaft)   |
           |                  | topic: user-events    |
           |                  | no ZooKeeper          |
           |                  +-----------+-----------+
           |                              |
           |                              v
           |                  +-----------------------+       +-----------+
           +----------------> | journal-service       | ----> | MongoDB 6 |
                    REST      | REST API, JWT         |       | journaldb |
                              | Kafka consumer        |       +-----------+
                              +-----------------------+
```

Clients call either service directly; there is no API gateway. Compose runs the databases, Kafka, and both services. In `dev` mode, Compose runs only Kafka and the databases while the Spring applications run on the host.

## Technology stack

| Component | Version or implementation |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.2 |
| Spring Security | Spring Boot-managed |
| User database | MySQL 8.0, Spring Data JPA |
| Journal database | MongoDB 6, Spring Data MongoDB |
| Kafka | Confluent `cp-kafka:7.5.1`, KRaft mode |
| JWT | JJWT 0.11.5, HMAC-SHA512 |
| OpenAPI UI | Springdoc 2.2.0 |
| JUnit Jupiter | 5.9.3 (explicitly declared in both POMs) |
| Mockito | 5.24.0 |
| Spring Kafka test support | Spring Boot-managed |
| JaCoCo | 0.8.15 |
| Container build | Maven 3.9 with Eclipse Temurin 21; Temurin 21 JRE runtime |

Dependency versions are taken from the service POM files or the image tags in Compose. Versions not explicitly declared by the POMs are described as Spring Boot-managed rather than inferred.

## Prerequisites

- Windows PowerShell for `dev.ps1`.
- Docker Desktop with the Docker Compose v2 plugin.
- Java 21 and Maven 3.9 or later for running the applications and tests on the host.
- Docker Buildx and registry access for publishing images.
- Optional: MySQL Workbench or MongoDB Compass for local database inspection.

The service directories include Maven wrapper scripts, but `dev.ps1` invokes `mvn` directly for host-mode services and tests.

## Repository structure

The tree below reflects the current workspace files.

```text
.
|-- .gitignore
|-- README.md
|-- dev.ps1
|-- docker-compose.yml
|-- docker-compose.local.yml
|-- mongo-init/
|   `-- 01-create-user.js
|-- postman/
|   `-- Microservices Collection.postman_collection.json
|-- user-service/
|   |-- .gitattributes
|   |-- .gitignore
|   |-- Dockerfile
|   |-- mvnw, mvnw.cmd
|   |-- pom.xml
|   |-- .mvn/wrapper/maven-wrapper.properties
|   `-- src/
|       |-- main/java/com/user_service/
|       |   |-- UserServiceApplication.java
|       |   |-- config/ (JacksonConfig, KafkaProducerConfig, OpenApiConfig, SecurityConfig)
|       |   |-- controller/ (AuthController, HealthController, UserController)
|       |   |-- dto/KafkaMessageDto.java
|       |   |-- entity/User.java
|       |   |-- exception/ (GlobalExceptionHandler, ResourceNotFoundException)
|       |   |-- modal/ (AuthRequest, AuthResponse, CustomUserDetails, Role)
|       |   |-- repository/UserRepository.java
|       |   |-- security/ (JwtAuthenticationFilter, JwtTokenProvider)
|       |   `-- service/ (CustomUserDetailsService, KafkaProducerService, UserService)
|       |-- main/resources/application.properties
|       `-- test/java/com/user_service/
|           |-- UserServiceApplicationTests.java
|           `-- tests/UserOperationsTests.java
`-- journal-service/
    |-- .gitattributes
    |-- .gitignore
    |-- Dockerfile
    |-- mvnw, mvnw.cmd
    |-- pom.xml
    |-- .mvn/wrapper/maven-wrapper.properties
    `-- src/
        |-- main/java/com/journal_service/
        |   |-- JournalServiceApplication.java
        |   |-- config/ (JacksonConfig, OpenApiConfig, SecurityConfig)
        |   |-- controller/ (AuthController, HealthController, JournalController)
        |   |-- dto/ (JournalDto, UserDto)
        |   |-- entity/Journal.java
        |   |-- exception/ (GlobalExceptionHandler, ResourceNotFoundException)
        |   |-- modal/ (AuthRequest, AuthResponse, CustomUserDetails, Role)
        |   |-- repository/JournalRepository.java
        |   |-- security/ (JwtAuthenticationFilter, JwtTokenProvider)
        |   `-- service/ (CustomUserDetailsService, JournalService, KafkaConsumerService)
        |-- main/resources/application.properties
        `-- test/java/com/journal_service/JournalServiceApplicationTests.java
```

## Configuration

Create a root `.env` file for Compose modes. Do not commit it. The repository does not include an `.env.example` file. Use unique, generated values in place of these placeholders:

```dotenv
MYSQL_ROOT_PASSWORD=<generated-mysql-root-password>
MYSQL_APP_PASSWORD=<generated-mysql-application-password>
MONGO_ROOT_PASSWORD=<generated-mongodb-root-password>
MONGO_APP_PASSWORD=<generated-mongodb-application-password>
JWT_SECRET=<generated-secret-at-least-64-bytes>
JOURNAL_ADMIN_EMAIL=admin@example.com
JOURNAL_ADMIN_PASSWORD=<generated-journal-admin-password>
IMAGE_TAG=latest
USER_DDL_AUTO=update
```

`JWT_SECRET` is used with HMAC-SHA512 in both services and must be at least 64 bytes. The exact same value must be provided to both services. Compose inserts `MONGO_APP_PASSWORD` directly into a MongoDB URI; use a URI-safe password (hexadecimal characters are suitable) unless the URI value is encoded correctly.

The table lists variables interpolated by Compose or read by the application properties. Compose also sets several fixed container values, such as service ports, database names, Kafka listeners, the Mongo application username (`journalapp`), and topic names; those fixed values are visible in `docker-compose.yml`.

### MongoDB initialization

Compose mounts `mongo-init/01-create-user.js` into MongoDB's `/docker-entrypoint-initdb.d` directory. On first initialization of an empty MongoDB data volume, the script switches to `journaldb` and creates the application user using `MONGO_APP_USER` and `MONGO_APP_PASSWORD`, granting that user the `readWrite` role on `journaldb`. Compose sets `MONGO_APP_USER` to `journalapp` and supplies the password from the root `.env`. The journal service connects with that application account; the MongoDB root account is separate.

MongoDB's initialization scripts are not rerun for an already-initialized data volume. If the application user was not created on first initialization, inspect the volume and credentials; resetting the stack with `.\dev.ps1 reset` deletes persisted database and Kafka data.

| Variable | Service or scope | Required? | Default | Purpose |
|---|---|---:|---|---|
| `MYSQL_ROOT_PASSWORD` | Compose / MySQL | Yes | None | MySQL root credential; Compose rejects an unset or empty value. |
| `MYSQL_APP_PASSWORD` | Compose / MySQL, user-service | Yes | None | Creates the MySQL `appuser` password and supplies user-service `DB_PASSWORD`. |
| `MONGO_ROOT_PASSWORD` | Compose / MongoDB | Yes | None | MongoDB root credential. |
| `MONGO_APP_PASSWORD` | Compose / MongoDB, journal-service | Yes | None | Creates the `journalapp` credential and is inserted into journal-service `DB_URI`. |
| `JWT_SECRET` | Both services | Yes | None | Signs and validates JWTs; must be identical across services and at least 64 bytes for HS512. |
| `JOURNAL_ADMIN_PASSWORD` | Compose / journal-service | Yes | None | Supplies the journal service's in-memory admin password as `ADMIN_PASSWORD`. |
| `JOURNAL_ADMIN_EMAIL` | Compose / journal-service | No | `admin@example.com` | Supplies the in-memory admin email as `ADMIN_EMAIL`. |
| `IMAGE_TAG` | Compose image selection | No | `latest` | Selects the user-service and journal-service image tag. |
| `USER_DDL_AUTO` | Compose / user-service | No | `update` | Supplies the user-service JPA schema mode as `DDL_AUTO`. |
| `KAFKA_CLUSTER_ID` | Compose / Kafka | No | `MkU3OEVBNTcwNTJENDM2Qk` | KRaft Kafka cluster identifier. |
| `SERVER_PORT` | Both applications | No | `8081` user-service; `8082` journal-service | Application HTTP port. |
| `DB_URL` | user-service | No | `jdbc:mysql://localhost:3307/userdb` | MySQL JDBC URL. Compose overrides it for the container network. |
| `DB_USERNAME` | user-service | No | `appuser` | MySQL application username. |
| `DB_PASSWORD` | user-service | Yes | None | MySQL application password. In Compose it is set from `MYSQL_APP_PASSWORD`. |
| `DDL_AUTO` | user-service | No | `update` | Hibernate schema behavior. Compose maps `USER_DDL_AUTO` to this variable. |
| `SHOW_SQL` | user-service | No | `false` | Enables Hibernate SQL logging. |
| `HIBERNATE_FORMAT_SQL` | user-service | No | `false` | Formats Hibernate SQL log output. |
| `LOG_LEVEL_ROOT` | Both applications | No | `info` | Root logging level. |
| `LOG_LEVEL_SPRING` | Both applications | No | `warn` | Spring logging level. |
| `LOG_LEVEL_SQL` | user-service | No | `warn` | Hibernate SQL logger level. |
| `LOG_LEVEL_SQL_BIND` | user-service | No | `warn` | Hibernate JDBC bind logger level. |
| `DB_URI` | journal-service | Yes | None | MongoDB connection URI. |
| `KAFKA_BOOTSTRAP_SERVERS` | Both applications | No | `localhost:29092` | Kafka bootstrap address; both services need to reach the same broker. |
| `GROUP_ID` | journal-service | No | `journal-service-group` | Kafka consumer group. |
| `TOPIC_USER_CREATED` | Both applications | No | `user-events` | User-operation topic; it must match for the consumer to receive producer events. |
| `TOPIC_USER_FAILED` | Both applications | No | `user-operation-failed` | Declared failure-topic configuration; the current producer and consumer do not use it. |
| `SWAGGER_ENABLED` | Both applications | No | `false` | Enables or disables OpenAPI docs and Swagger UI. The local Compose overlay and host dev commands enable it. |
| `JWT_EXPIRATION` | Both applications | No | `3600000` milliseconds | JWT lifetime when that service issues a token. |
| `ADMIN_EMAIL` | journal-service | No | `admin@example.com` | Email for the journal service's in-memory admin account. Compose maps `JOURNAL_ADMIN_EMAIL` here. |
| `ADMIN_PASSWORD` | journal-service | Yes | None | Password for the journal service's in-memory admin account. Compose maps `JOURNAL_ADMIN_PASSWORD` here. |

For direct host runs, provide the required application variables yourself. The `dev.ps1 user` and `dev.ps1 journal` modes load the root `.env` and set the service-specific variables before starting Maven.

## Run modes

Run these commands from the repository root in Windows PowerShell. Create `.env` before starting Compose; Compose requires its database, JWT, and journal-admin credentials.

| Mode | Exact command | Use |
|---|---|---|
| `dev` infrastructure | `.\dev.ps1 infra` | Runs Kafka and the databases in Docker, stopping any containerized service instances. Then run `.\dev.ps1 user` and `.\dev.ps1 journal` in separate terminals to run the applications on the host. |
| `dev` user application | `.\dev.ps1 user` | Starts user-service with host database/Kafka addresses, `.env` credentials, and Swagger enabled. |
| `dev` journal application | `.\dev.ps1 journal` | Starts journal-service with host database/Kafka addresses, `.env` credentials, and Swagger enabled. |
| `local` | `.\dev.ps1 local` | Recreates the Compose stack and builds both service images from the local Dockerfiles. |
| `hub` | `.\dev.ps1 hub` | Pulls the configured published image tag and starts the Compose stack. |
| Stop | `.\dev.ps1 down` | Stops and removes Compose containers and networks; named data volumes are retained. |
| Logs | `.\dev.ps1 logs` | Follows Compose logs, showing the most recent 100 lines per service. |
| Reset | `.\dev.ps1 reset` | After typing `yes`, stops the stack and removes named data volumes, deleting database and Kafka data. |
| Tests | `.\dev.ps1 test` | Runs `mvn -B test` in each service directory. |
| Publish | `.\dev.ps1 publish -Tag 1.0.1` | Builds and pushes both service images for `linux/amd64`, with the requested tag and `latest`. |

Only one mode should run at a time because the modes share container names and ports. The `hub` mode uses the base Compose file; `local` and `dev` infrastructure use the local overlay.

Plain Docker Compose equivalents:

```powershell
# Local build: all services in Docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build -d

# Pull and start published images
docker compose -f docker-compose.yml pull
docker compose -f docker-compose.yml up -d

# Stop without deleting named data volumes
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

The Compose files publish user-service on port `8081` and journal-service on port `8082`. The local overlay additionally publishes Kafka on host port `29092`, MySQL on `127.0.0.1:3307`, and MongoDB on `127.0.0.1:27018`. The base Compose file does not publish database or Kafka ports to the host.

## API reference

Routes and access rules below are derived from the controllers and security configuration. Request bodies are summaries of the Java request model; no example credentials are included.

### user-service

| Method | Path | Authentication / role | Request body | Responses |
|---|---|---|---|---|
| `GET` | `/` | Public | None | `200` plain-text service status. |
| `POST` | `/api/auth/login` | Public | `email`, `password` | `200` token response; generic handled exception returns `500`. |
| `POST` | `/api/auth/logout` | Public by the current request matcher | None | `200` plain-text logout message. Logout clears the current security context but does not revoke an already issued JWT. |
| `POST` | `/api/users` | Public | `name`, `email`, `password`, `roles` (`ADMIN` or `USER`) | `201` created user; caught operation failure returns `500`. |
| `GET` | `/api/users` | `ADMIN` | None | `200` list of users. |
| `GET` | `/api/users/{id}` | Authenticated `ADMIN`, or the user whose ID matches `{id}` | None | `200` user; caught lookup failure returns `404`. |
| `PUT` | `/api/users/{id}` | `ADMIN` | User fields to update: `name`, `email`, `password`, `roles` | `200` updated user; caught operation failure returns `500`. |
| `DELETE` | `/api/users/{id}` | `ADMIN` | None | `200` deleted user; caught lookup failure returns `404`. |

### journal-service

| Method | Path | Authentication / role | Request body | Responses |
|---|---|---|---|---|
| `GET` | `/` | Public | None | `200` plain-text service status. |
| `POST` | `/api/auth/login` | Public | `email`, `password` | `200` token response; generic handled exception returns `500`. |
| `POST` | `/api/journals` | Public by the current controller annotations | Journal fields, including user identity, operation, status, comment, and operation time | `201` created journal; generic handled exception returns `500`. |
| `GET` | `/api/journals` | `ADMIN` | None | `200` list of journals. |
| `GET` | `/api/journals/user/{userId}` | `ADMIN` | None | `200` list of journals for the user ID. |
| `GET` | `/api/journals/{id}` | `ADMIN` | None | `200` journal; missing ID returns `404`. |
| `PUT` | `/api/journals/{id}` | `ADMIN` | Journal fields to update | `200` updated journal; missing ID returns `404`. |
| `DELETE` | `/api/journals/{id}` | `ADMIN` | None | `204` on deletion; missing ID returns `404`. |

The actuator endpoint `/actuator/health` is configured for exposure, but it is not a controller route and is not included in the controller tables. Both security configurations require authentication for it. In contrast, the root controller route `/` is public.

### Postman collection cross-check

The collection contains user login/logout and user create/list/get-by-ID/update/delete requests. It also contains journal login and get-all/get-by-ID entries.

- The journal get-by-ID request has no URL configured.
- The collection omits journal create, get-by-user-ID, update, and delete requests that exist in the controller.
- The collection includes credential examples that do not match environment-based journal admin provisioning. Do not rely on those examples; use configured credentials without copying them into documentation.
- User-service has no seeded account. Registering an account is needed before login, but the public registration endpoint accepts an `ADMIN` role. Do not expose this service publicly in its current state.

## Kafka

| Item | Current behavior |
|---|---|
| Broker | Confluent Kafka 7.5.1 in KRaft mode; Compose configures no ZooKeeper service. |
| Main topic | `user-events` by default, configurable through `TOPIC_USER_CREATED`. The producer and consumer must use the same topic and broker. |
| Configured failure topic | `user-operation-failed` by default, configurable through `TOPIC_USER_FAILED`. It is declared in properties and Compose but is not used by the current producer or consumer. |
| Producer | user-service publishes create, get-by-ID, update, delete, login, and logout operation records to the main topic. The get-all-users route does not publish an event. Success and failure records use the same topic. |
| Event payload | `operation`, `status`, `comment`, and nested `user`. The nested value is serialized from the `User` entity; its model includes `userId`, `name`, `email`, `password`, `roles`, `createdAt`, and `updatedAt`. |
| Consumer | journal-service listens only to the main topic, deserializes the event and writes a journal record to MongoDB. It sets `operationTime` when consuming. It does not branch on operation type, so an otherwise valid unknown operation value is stored as an operation string. |
| Malformed message | The consumer catches and logs processing errors; it does not write a journal entry for a message that fails parsing and no separate dead-letter/failure-topic handling is configured. |

## Security

- Both services use stateless bearer JWTs signed with HMAC-SHA512. Both must be configured with the same `JWT_SECRET`; the default token lifetime is one hour.
- user-service authenticates users stored in MySQL and encodes passwords during account creation with BCrypt. Its JWT includes the user ID as subject and includes email and roles as claims. The current update path does not encode a newly supplied password.
- journal-service provisions one in-memory admin identity from `ADMIN_EMAIL` and required `ADMIN_PASSWORD`; it does not load that admin from MongoDB. The password is BCrypt-encoded when the user-details service is constructed. The admin email defaults to `admin@example.com`.
- `ADMIN` is required for user listing, updating, and deletion, and for all journal retrieval/update/delete endpoints. An authenticated user can also fetch their own user record by ID.
- Public controller routes include each service's `GET /`, each `/api/auth/**` route in user-service, user-service `POST /api/users`, journal-service `POST /api/auth/login`, and journal-service `POST /api/journals`. The broader `/api/journals/**` security matcher permits requests through the filter chain, while method annotations restrict the journal methods marked `ADMIN`.
- Swagger and API-doc routes are permitted by the security configurations but disabled by default; `SWAGGER_ENABLED=true` enables them.
- `/actuator/health` is exposed but is not on either security allowlist, so it requires authentication. The Compose health checks call it without a token; see Findings below.
- The API and Kafka listeners use plain HTTP/PLAINTEXT configuration; no TLS termination or Kafka TLS is configured in these files.

## Testing and coverage

Run tests per service:

```powershell
cd user-service
mvn -B test
```

```powershell
cd journal-service
mvn -B test
```

From the repository root, `.\dev.ps1 test` runs `mvn -B test` in both services. To run Maven's verify lifecycle and generate the configured JaCoCo reports, run:

```powershell
cd user-service
mvn -B verify
```

```powershell
cd journal-service
mvn -B verify
```

JaCoCo report location in each service: `target/site/jacoco/index.html`. The current POMs configure the JaCoCo agent and report but do not configure a coverage check or minimum threshold. The requested build target is 80% line coverage on the bundle, excluding DTO, entity, modal, config, and application classes; that check is not active until the POMs are updated.

Existing tests are `UserOperationsTests` and `UserServiceApplicationTests` in user-service, and an empty `JournalServiceApplicationTests` class in journal-service. The Dockerfiles build with tests skipped; run the Maven commands above to execute tests.

## Publishing and releasing images

The Compose image names use the `sahilsheikhdev` namespace. The publish command in `dev.ps1` builds and pushes each service for `linux/amd64`, tagging each image with both the supplied tag and `latest`:

```powershell
.\dev.ps1 publish -Tag 1.0.1
```

Authenticate to the image registry and choose a release tag before publishing. For deployments, set `IMAGE_TAG` to the release tag rather than relying on `latest`. The `hub` run mode pulls the image tag selected by `IMAGE_TAG`.

## Troubleshooting

| Symptom | Check or action |
|---|---|
| Compose reports a required variable is empty or missing | Check the root `.env` values used by Compose, especially database passwords, `JWT_SECRET`, and `JOURNAL_ADMIN_PASSWORD`. |
| Service fails to connect to MySQL or MongoDB | Check container health and credentials. Database initialization scripts run only when the corresponding data volume is empty; changing credentials may require a deliberate data reset. |
| MongoDB URI authentication fails | Check `MONGO_APP_PASSWORD`, `authSource=journaldb`, and URI-safe characters in the password. |
| Kafka client cannot connect | Use `localhost:29092` for host-run services and `kafka:9092` for Compose services. Confirm both services use the same Kafka cluster and `TOPIC_USER_CREATED`. |
| Service container remains unhealthy | Inspect `docker compose logs <service>`. The configured health check requests `/actuator/health` without authentication, while the security configuration does not permit anonymous access to that endpoint. |
| Swagger UI is unavailable | Set `SWAGGER_ENABLED=true`; it is disabled by default. Local Compose and host dev modes set it to true. |
| JWT is rejected after changing the signing key | Set the same `JWT_SECRET` for both services and obtain a new token. The code uses HS512, which requires at least a 64-byte key. |
| Host port is already in use | Stop the other run mode or the conflicting process; the script modes share fixed container names and ports. |
| MySQL/MongoDB/Kafka data must be removed | `.\dev.ps1 reset` prompts before removing the Compose named volumes. This permanently deletes their persisted data. |
| Image pull fails | Retry the pull and check Docker registry authentication and network/proxy connectivity. |

## Known limitations

- Compose publishes the application HTTP ports without a TLS configuration; the base file binds those ports on the host's default interfaces.
- The local overlay binds database and Kafka host ports to loopback, but that does not change the application ports published by the base Compose file.
- User-service defaults to `spring.jpa.hibernate.ddl-auto=update`; no database migration tool is configured in the POMs.
- Kafka listeners use PLAINTEXT; no Kafka authentication or TLS is configured.
- `TOPIC_USER_FAILED` is configured but unused. Producer failures are not routed to a separate topic, and the consumer has no dead-letter handling.
- Journal records and account data have no retention or cleanup policy configured in the source files.
- The user-service controller serializes `User` entities in its API responses, and the Kafka event nests that entity. The entity includes a password property; callers should not receive or publish that field.
- user-service `updateUser` assigns a supplied password directly instead of BCrypt-encoding it. Do not use the update route to change passwords until this is corrected.
- Logout clears the current request's security context but does not revoke stateless JWTs that were already issued.
- Both actuator health checks call a protected endpoint without credentials, which can cause containers to fail their health checks.

## Author

**Sahil Sheikh** - [GitHub](https://github.com/sahilsheikh-dev)
