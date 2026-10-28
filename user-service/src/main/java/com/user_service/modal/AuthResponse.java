package com.user_service.modal;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;

    public AuthResponse(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

}
