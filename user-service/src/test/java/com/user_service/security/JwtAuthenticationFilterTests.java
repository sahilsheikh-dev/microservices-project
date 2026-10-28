package com.user_service.security;

import com.user_service.modal.CustomUserDetails;
import com.user_service.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTests {
    private static final String SECRET = "0123456789".repeat(7);
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        provider = new JwtTokenProvider(SECRET, 3_600_000, "user-service");
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(provider, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validJwt_setsAuthenticatedUser() throws Exception {
        CustomUserDetails user = CustomUserDetails.builder().id(7L).email("db@example.com")
                .authorities(List.of()).build();
        when(userDetailsService.loadUserById(7L)).thenReturn(user);

        assertContinues(token("user-service", Instant.now().plusSeconds(60)), true);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
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

    @Test
    void doFilterInternal_deletedUser_continuesUnauthenticated() throws Exception {
        when(userDetailsService.loadUserById(7L)).thenThrow(new UsernameNotFoundException("deleted"));

        assertContinues(token("user-service", Instant.now().plusSeconds(60)), false);
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
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject("7")
                .claim("email", "claim@example.com")
                .claim("roles", List.of("ROLE_USER"))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }
}
