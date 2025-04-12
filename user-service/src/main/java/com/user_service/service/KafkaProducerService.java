package com.user_service.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user_service.dto.KafkaMessageDto;
import com.user_service.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, @Value("${app.kafka.topic.userCreated}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void sendMessage(String message) {
        kafkaTemplate.send(topic, message);
    }

    public void sendMessage(String operation, String status, String comment, User user) {
        try {
            KafkaMessageDto kafkaMessageDto = new KafkaMessageDto(operation, status, comment, user);
            String message = objectMapper.writeValueAsString(kafkaMessageDto);
            kafkaTemplate.send(topic, message);
        } catch (JsonProcessingException e) {
            e.getMessage();
        }
    }

    public void sendLogoutEvent(Long userId, String email) {
        Map<String, Object> logoutEvent = new HashMap<>();
        logoutEvent.put("id", userId);
        logoutEvent.put("email", email);
        logoutEvent.put("eventType", "LOGOUT");
        logoutEvent.put("timestamp", LocalDateTime.now().toString());

        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(logoutEvent);
            kafkaTemplate.send(topic, message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            e.getMessage();
        }
    }


}
