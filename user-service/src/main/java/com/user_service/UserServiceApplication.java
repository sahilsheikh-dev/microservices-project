package com.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.user_service.entity.User;
import com.user_service.service.UserService;

@SpringBootApplication
public class UserServiceApplication {
    private static final Logger log = LoggerFactory.getLogger(UserServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner bootstrapAdmin(
            UserService userService,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            User admin = userService.createBootstrapAdmin(email, password);
            if (admin != null) {
                log.info("Bootstrapped administrator account for {}", admin.getEmail());
            }
        };
    }
}
