package com.journal_service.controller;

import com.journal_service.config.SecurityConfig;
import com.journal_service.entity.Journal;
import com.journal_service.exception.ApiErrorWriter;
import com.journal_service.security.JwtAuthenticationFilter;
import com.journal_service.security.JwtTokenProvider;
import com.journal_service.service.JournalService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JournalController.class, properties = {
        "app.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "app.jwt.issuer=user-service"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        ApiErrorWriter.class, JournalSecurityWebMvcTests.HealthEndpoint.class})
class JournalSecurityWebMvcTests {
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private JournalService journalService;

    @Test
    void protectedJournalEndpoints_anonymousUserRoleAndAdmin_enforceAuthorization() throws Exception {
        List<String> paths = List.of("/api/journals", "/api/journals/user/12", "/api/journals/abc");
        when(journalService.getAllJournals(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(journalService.getJournalsByUserId(eq(12L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(journalService.getJournalById("abc")).thenReturn(Journal.builder().id("abc").build());

        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", bearer(7L, "admin@example.com", "USER")))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete("/api/journals/abc")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/journals/abc")
                        .header("Authorization", bearer(7L, "admin@example.com", "USER")))
                .andExpect(status().isForbidden());

        for (String path : paths) {
            mockMvc.perform(get(path).header("Authorization", bearer(1L, "admin@example.com", "ADMIN")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(delete("/api/journals/abc")
                        .header("Authorization", bearer(1L, "admin@example.com", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
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

    @RestController
    static class HealthEndpoint {
        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }
}
