# 🚀 Microservices Project: User Service & Journal Service

An enterprise-ready microservices architecture built using **Spring Boot 3.x**, **Java 21**, **Kafka**, **Docker Compose**, **JWT Security**, **MySQL**, and **MongoDB**.

This system handles user operations and maintains a journal of user activity events through seamless inter-service communication via Kafka.

---

## 🛠️ Getting Started

Follow this guide to set up and run the **User Service** and **Journal Service**.

### ✅ Prerequisites

Ensure the following tools are installed on your system:

- **Docker** 🐳
- **Java 21** ☕️
- **Maven** (v3.6+)
- **IDE like IntelliJ IDEA/VS Code/Spring Tool Suite (STS)**
- **MySQL Workbench** (optional, for DB inspection)
- **MongoDB Compass** (optional, for DB inspection)

---

## 🔄 Step 1: Clone the Repository

```bash
git clone https://github.com/sahilsheikh-dev/microservices-project.git
cd microservices-project
```

---

## ⚙️ Step 2: Select Your Docker Setup

This project includes **two Docker Compose configurations**:

- `local-docker-compose.yml` → For building and running services **locally**
- `hub-docker-compose.yml` → For running services using **Docker Hub images**

> 💡 **Tip:** You can rename either of these files to `docker-compose.yml` if you want to use the simpler `docker-compose up` command without specifying the file; or you can run either file explicitly like mentioned below:

---

### 🛠️ Option 1: Build and Run Locally

Build the services:

```bash
cd user-service && mvn clean install
cd ../journal-service && mvn clean install
```

Start the services:

```bash
docker-compose -f local-docker-compose.yml up --build
# Or run in detached mode
docker-compose -f local-docker-compose.yml up -d

# To stop containers and remove containers, networks, volumes, and images created by up
docker-compose -f local-docker-compose.yml down
```

---

### 📦 Option 2: Use Prebuilt Docker Images from Docker Hub

Pull and run the services directly using Docker Hub images:

```bash
docker-compose -f hub-docker-compose.yml pull
docker-compose -f hub-docker-compose.yml up -d

# To stop containers and remove containers, networks, volumes, and images created by up
docker-compose -f hub-docker-compose.yml down
```

✅ Available Images:
- `sahilsheikh/user-service:latest`
- `sahilsheikh/journal-service:latest`

> 🔄 **To rebuild and push updated images:**
```bash
docker build -t sahilsheikh/user-service:latest ./user-service
docker push sahilsheikh/user-service:latest

docker build -t sahilsheikh/journal-service:latest ./journal-service
docker push sahilsheikh/journal-service:latest
```

--- 

## 🌐 Step 3: Access Services

### 🔍 Swagger UI

| Service         | URL                                       |
|-----------------|--------------------------------------------|
| User Service    | [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html) |
| Journal Service | [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html) |

### 🗄️ Database Access

| Service         | DB Type | URL                                      |
|-----------------|---------|------------------------------------------|
| User Service    | MySQL   | `jdbc:mysql://localhost:3307/userdb`     |
| Journal Service | MongoDB | `mongodb://localhost:27017/journaldb`    |

---

## 🔑 Default Credentials

| Service         | Username              | Password   |
|-----------------|------------------------|------------|
| User Service    | _Create via API_       | —          |
| Journal Service | `admin@example.com`    | `admin123` |

🔒 **Roles & Access Control:**

- **ADMIN** → Full access
- **USER** → Limited access (User creation, self-profile)

---

## 🎯 Step 4: Use with Postman

Use the provided [Postman collection](./postman) to test APIs.

### Steps:
1. Register a new admin user: `POST /api/users`
2. Login and retrieve your JWT token.
3. Set the token in your collection or folder's **Authorization > Bearer Token** section.

---

## 🧪 Troubleshooting


| Issue                              | Solution                                                                 |
|-----------------------------------|--------------------------------------------------------------------------|
| 🔴 Ports already in use            | Kill conflicting processes or change ports in config                     |
| 🔴 MySQL/MongoDB connection error | Ensure Docker containers are running properly                            |
| 🔴 JWT expired/invalid             | Login again and use the latest token                                     |

---

## 📌 Project Overview

This project consists of two main microservices:

1. **User Service**
   - Handles user operations like registration, login, logout, retrieval, updates, and deletion.
   - Manages user roles (ADMIN, USER).
   - Publishes events on user activities to Kafka for all user operations.

2. **Journal Service**
   - Consumes user events from Kafka.
   - Persists journal entries in **MongoDB** for all user opearations.
   - Exposes endpoints to retrieve user event logs.
   - Secure access based on roles.

---

## 🏗️ System Architecture

```plaintext
[ Client (Postman / Frontend) ]
           |
    +------v-------+
    |  API Gateway |
    +------|-------+
           |
    +------v---------------------+
    | User Service (Spring Boot) |
    | - REST API                 |
    | - Kafka Producer           |
    | - MySQL                    |
    +------|---------------------+
           |
    Kafka Topic: user-events
           |
    +------v------------------------+
    | Journal Service (Spring Boot) |
    | - Kafka Consumer              |
    | - REST API                    |
    | - MongoDB                     |
    +-------------------------------+
```

---

## 🧩 Tech Stack

- **Java 21**
- **Spring Boot 3.x**
- **Spring Security (JWT)**
- **Spring Data JPA / MongoDB**
- **Kafka & Zookeeper**
- **Docker + Compose**
- **MySQL & MongoDB**
- **Swagger (OpenAPI)**
- **JUnit 5 + Mockito**
- **JaCoCo (Test Coverage)**

---

## 👨‍💻 Author

Built with ❤️ by **Sahil Sheikh** - [GitHub](https://github.com/sahilsheikh-dev)
