package com.user_service.security;

import com.user_service.modal.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private final String jwtSecret;
    private final long jwtExpirationInMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-in-ms}") long jwtExpirationInMs,
            @Value("${app.jwt.issuer:user-service}") String issuer) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 64 bytes for HS512.");
        }

        this.jwtSecret = jwtSecret;
        this.jwtExpirationInMs = jwtExpirationInMs;
        this.issuer = issuer;
    }

    public long getExpirationInSeconds() {
        return jwtExpirationInMs / 1000;
    }

    public String generateToken(Authentication authentication) {

        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        claims.put("email", userPrincipal.getUsername());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(Long.toString(userPrincipal.getId()))
                .setIssuer(issuer)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }

    public Long getUserIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                    .requireIssuer(issuer).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException ex) {
            return false;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
