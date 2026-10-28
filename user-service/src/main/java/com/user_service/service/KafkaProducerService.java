package com.user_service.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user_service.dto.KafkaMessageDto;
import com.user_service.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, @Value("${app.kafka.topic.userCreated}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
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

}
