package com.user_service.controller;

import com.user_service.config.SecurityConfig;
import com.user_service.dto.UserResponse;
import com.user_service.entity.User;
import com.user_service.exception.ApiErrorWriter;
import com.user_service.exception.ConflictException;
import com.user_service.modal.CustomUserDetails;
import com.user_service.security.JwtAuthenticationFilter;
import com.user_service.security.JwtTokenProvider;
import com.user_service.service.CustomUserDetailsService;
import com.user_service.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, properties = {
        "app.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "app.jwt.expiration-in-ms=3600000",
        "app.jwt.issuer=user-service"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        ApiErrorWriter.class, UserSecurityWebMvcTests.HealthEndpoint.class})
class UserSecurityWebMvcTests {
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        User admin = user(5L, "admin@example.com", "ADMIN");
        User user = user(6L, "user@example.com", "USER");
        when(userDetailsService.loadUserById(5L)).thenReturn(CustomUserDetails.build(admin));
        when(userDetailsService.loadUserById(6L)).thenReturn(CustomUserDetails.build(user));
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(admin)));
        when(userService.getUserById(anyLong())).thenAnswer(invocation -> user(invocation.getArgument(0),
                "user@example.com", "USER"));
        when(userService.updateUser(eq(5L), any())).thenAnswer(invocation -> user(5L, "admin@example.com", "ADMIN"));
        when(userService.deleteUser(5L)).thenReturn(admin);
        when(userService.createUser(any())).thenReturn(new UserResponse(7L, "Person", "person@example.com",
                "USER", null, null));
    }

    @Test
    void protectedUserEndpoints_anonymousWrongRoleAndAllowedPrincipal_enforceSecurity() throws Exception {
        for (String endpoint : List.of("/api/users", "/api/users/me", "/api/users/5")) {
            mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(put("/api/users/5").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/5")).andExpect(status().isUnauthorized());
        for (String endpoint : List.of("/api/users/5", "/api/users")) {
            mockMvc.perform(get(endpoint).header("Authorization", bearer(6L, "user@example.com", "USER")))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/users").header("Authorization", bearer(5L, "admin@example.com", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(6L, "user@example.com", "USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/5").header("Authorization", bearer(5L, "admin@example.com", "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/5")
                        .header("Authorization", bearer(6L, "user@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/5")
                        .header("Authorization", bearer(5L, "admin@example.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/5")
                        .header("Authorization", bearer(6L, "user@example.com", "USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/5")
                        .header("Authorization", bearer(5L, "admin@example.com", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void registration_publicAndValidationErrors_includeFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Person\",\"email\":\"person@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void registration_duplicateEmail_returns409Conflict() throws Exception {
        when(userService.createUser(any())).thenThrow(
                new ConflictException("An account with this email already exists."));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Person\",\"email\":\"person@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void health_actuatorEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String bearer(long id, String email, String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
                .setIssuer("user-service")
                .setSubject(Long.toString(id))
                .claim("email", email)
                .claim("roles", List.of("ROLE_" + role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }

    private User user(Long id, String email, String roles) {
        return User.builder().userId(id).name(email.startsWith("admin") ? "Admin" : "User")
                .email(email).password("encoded").roles(roles).build();
    }

    @RestController
    static class HealthEndpoint {
        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }
}
