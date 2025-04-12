package com.user_service.controller;

import com.user_service.entity.User;
import com.user_service.modal.AuthRequest;
import com.user_service.modal.AuthResponse;
import com.user_service.modal.CustomUserDetails;
import com.user_service.repository.UserRepository;
import com.user_service.security.JwtTokenProvider;
import com.user_service.service.KafkaProducerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private KafkaProducerService kafkaProducerService;
    @Autowired
    private UserRepository userRepository;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@RequestBody AuthRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userDetails.getId()));

            kafkaProducerService.sendMessage("LOGIN", "SUCCESS", "User logged in successfully.", user);

            return ResponseEntity.ok(new AuthResponse(jwt));
        } catch (Exception ex) {
            userRepository.findByEmail(loginRequest.getEmail()).ifPresent(
                    user -> kafkaProducerService.sendMessage("LOGIN", "FAILURE", ex.getMessage(), user)
            );
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logoutUser(Authentication authentication) {
        User user = new User();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            user.setUserId(userDetails.getId());
            user.setName(userDetails.getUsername());
            user.setEmail(userDetails.getEmail());
        }
        try {
            kafkaProducerService.sendMessage("LOGOUT", "SUCCESS", "User logged out successfully.", user);
            SecurityContextHolder.clearContext();
            return ResponseEntity.ok("You have been successfully logged out.");
        } catch (Exception ex) {
            kafkaProducerService.sendMessage("LOGOUT", "FAILURE", ex.getMessage(), user);
            throw ex;
        }
    }

}
