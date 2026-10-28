package com.user_service.controller;

import com.user_service.modal.AuthRequest;
import com.user_service.modal.AuthResponse;
import com.user_service.modal.CustomUserDetails;
import com.user_service.security.JwtTokenProvider;
import com.user_service.service.LoginAttemptService;
import com.user_service.service.UserEventPublisher;
import com.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserEventPublisher eventPublisher;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody AuthRequest loginRequest) {
        String email = UserService.normalizeEmail(loginRequest.getEmail());
        loginAttemptService.checkAllowed(email);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(email);
            eventPublisher.publish("LOGIN", "FAILURE", "Invalid credentials", null, null, email, null);
            throw ex;
        }

        loginAttemptService.recordSuccess(email);
        String jwt = tokenProvider.generateToken(authentication);
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        eventPublisher.publish("LOGIN", "SUCCESS", "User logged in", principal.getId(), principal.getName(),
                principal.getEmail(), principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .findFirst().orElse("USER"));
        return ResponseEntity.ok(new AuthResponse(jwt, tokenProvider.getExpirationInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logoutUser(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        eventPublisher.publish("LOGOUT", "SUCCESS", "User logged out", principal.getId(), principal.getName(),
                principal.getEmail(), principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .findFirst().orElse("USER"));
        return ResponseEntity.ok("Logout recorded. The token remains valid until it expires.");
    }
}
