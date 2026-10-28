package com.user_service.modal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
    @NotBlank
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
