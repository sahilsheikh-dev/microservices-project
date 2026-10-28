package com.user_service.security;

import com.user_service.modal.CustomUserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTests {
    private final String secret = "0123456789".repeat(7);
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(secret, 3_600_000, "user-service");
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
    void generateToken_authenticatedPrincipal_includesIdentityAndRoleClaims() {
        CustomUserDetails user = CustomUserDetails.builder()
                .id(9L)
                .name("Admin")
                .email("admin@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        String jwt = provider.generateToken(authentication);

        assertThat(provider.validateToken(jwt)).isTrue();
        assertThat(provider.getUserIdFromJWT(jwt)).isEqualTo(9L);
        var claims = Jwts.parserBuilder().setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .requireIssuer("user-service").build().parseClaimsJws(jwt).getBody();
        assertThat(claims.get("email", String.class)).isEqualTo("admin@example.com");
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_ADMIN");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void constructor_shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", 1000, "user-service"))
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
