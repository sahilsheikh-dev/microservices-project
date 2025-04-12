package com.user_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(ObjectMapper objectMapper) {
        return args -> {
            LocalDateTime now = LocalDateTime.now();
            String json = objectMapper.writeValueAsString(now);
            System.out.println("Serialized LocalDateTime: " + json);
        };
    }

}
