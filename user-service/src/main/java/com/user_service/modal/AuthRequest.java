package com.user_service.modal;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;
}
