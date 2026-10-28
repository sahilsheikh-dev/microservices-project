package com.journal_service.security;

import com.journal_service.security.JournalPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JwtAuthenticationFilterTests {
    private static final String SECRET = "0123456789".repeat(7);
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(new JwtTokenProvider(SECRET, "user-service"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validJwt_setsPrincipalFromClaimsOnly() throws Exception {
        assertContinues(token("user-service", Instant.now().plusSeconds(60)), true);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(new JournalPrincipal(7L, "claim@example.com"));
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void doFilterInternal_adminEmailWithUserRole_doesNotGrantAdmin() throws Exception {
        assertContinues(token("user-service", Instant.now().plusSeconds(60),
                "admin@example.com", "ROLE_USER"), true);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void doFilterInternal_expiredJwt_continuesUnauthenticated() throws Exception {
        assertContinues(token("user-service", Instant.now().minusSeconds(1)), false);
    }

    @Test
    void doFilterInternal_tamperedJwt_continuesUnauthenticated() throws Exception {
        String valid = token("user-service", Instant.now().plusSeconds(60));
        int signatureStart = valid.lastIndexOf('.') + 1;
        char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';

        assertContinues(valid.substring(0, signatureStart) + replacement + valid.substring(signatureStart + 1), false);
    }

    @Test
    void doFilterInternal_wrongIssuerJwt_continuesUnauthenticated() throws Exception {
        assertContinues(token("other-issuer", Instant.now().plusSeconds(60)), false);
    }

    @Test
    void doFilterInternal_malformedJwt_continuesUnauthenticated() throws Exception {
        assertContinues("malformed", false);
    }

    @Test
    void doFilterInternal_missingAuthorizationHeader_continuesUnauthenticated() throws Exception {
        assertContinues(null, false);
    }

    private void assertContinues(String jwt, boolean authenticated) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (jwt != null) request.addHeader("Authorization", "Bearer " + jwt);
        AtomicBoolean continued = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> continued.set(true);

        assertThatCode(() -> filter.doFilterInternal(request, new MockHttpServletResponse(), chain))
                .doesNotThrowAnyException();

        assertThat(continued).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication() != null).isEqualTo(authenticated);
    }

    private String token(String issuer, Instant expiresAt) {
        return token(issuer, expiresAt, "claim@example.com", "ROLE_USER");
    }

    private String token(String issuer, Instant expiresAt, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject("7")
                .claim("email", email)
                .claim("roles", List.of(role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }
}
