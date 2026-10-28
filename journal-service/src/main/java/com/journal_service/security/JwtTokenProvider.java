package com.journal_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtTokenProvider {
    private final byte[] signingKey;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer:user-service}") String issuer) {
        this.signingKey = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 64) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 64 bytes for HS512.");
        }
        this.issuer = issuer;
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Long getUserIdFromJWT(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getEmailFromJWT(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public List<String> getRolesFromJWT(String token) {
        Object value = parseClaims(token).get("roles");
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("JWT roles claim must be a list.");
        }
        List<String> roles = new ArrayList<>(values.size());
        for (Object role : values) {
            if (!(role instanceof String stringRole) || stringRole.isBlank()) {
                throw new IllegalArgumentException("JWT roles claim contains an invalid role.");
            }
            roles.add(stringRole);
        }
        return List.copyOf(roles);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
