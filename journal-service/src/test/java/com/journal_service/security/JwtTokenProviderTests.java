package com.journal_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTests {
    private final String secret = "0123456789".repeat(7);
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(secret, "user-service");
    }

    @Test
    void validateToken_validIssuerAndSignature_acceptsToken() {
        assertThat(provider.validateToken(token("user-service", Instant.now().plusSeconds(60))))
                .isTrue();
    }

    @Test
    void validateToken_expiredToken_rejectsToken() {
        assertThat(provider.validateToken(token("user-service", Instant.now().minusSeconds(1))))
                .isFalse();
    }

    @Test
    void validateToken_tamperedSignature_rejectsToken() {
        String valid = token("user-service", Instant.now().plusSeconds(60));
        int signatureStart = valid.lastIndexOf('.') + 1;
        char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String changed = valid.substring(0, signatureStart) + replacement + valid.substring(signatureStart + 1);

        assertThat(provider.validateToken(changed)).isFalse();
    }

    @Test
    void validateToken_wrongIssuer_rejectsToken() {
        assertThat(provider.validateToken(token("another-issuer", Instant.now().plusSeconds(60))))
                .isFalse();
    }

    @Test
    void constructor_shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", "user-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 bytes");
    }

    private String token(String issuer, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject("1")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }
}
