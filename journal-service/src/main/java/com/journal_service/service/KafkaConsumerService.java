package com.journal_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journal_service.dto.UserDto;
import com.journal_service.entity.Journal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final JournalService journalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topic.userCreated}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String operation = jsonNode.get("operation").asText();
            String status = jsonNode.get("status").asText();
            String comment = jsonNode.get("comment").asText();
            UserDto userDto = objectMapper.treeToValue(jsonNode.get("user"), UserDto.class);

            Journal journal = Journal.builder()
                    .userId((userDto.getUserId() == null || userDto.getUserId() == 0) ? -1 : userDto.getUserId())
                    .userName((userDto.getName() == null || userDto.getName().trim().isEmpty()) ? "" : userDto.getName())
                    .userEmail((userDto.getEmail() == null || userDto.getEmail().trim().isEmpty()) ? "" : userDto.getEmail())
                    .userRole((userDto.getRoles() == null || userDto.getRoles().trim().isEmpty()) ? "" : userDto.getRoles())
                    .status((status == null || status.trim().isEmpty()) ? "" : status)
                    .comment((comment == null || comment.trim().isEmpty()) ? "" : comment)
                    .operation((operation == null || operation.trim().isEmpty()) ? "" : operation)
                    .operationTime(LocalDateTime.now())
                    .build();

            journalService.createJournal(journal);
            logger.info("Journal saved successfully for userId: {}", userDto.getUserId());
        } catch (Exception e) {
            logger.error("Error consuming message: {}", message, e);
        }
    }

}
