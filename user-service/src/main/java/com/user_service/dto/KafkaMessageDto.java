package com.user_service.dto;

import com.user_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaMessageDto {
    private String operation;
    private String status;
    private String comment;
    private User user;
}
