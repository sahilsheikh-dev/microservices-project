package com.user_service.controller;

import com.user_service.modal.AuthRequest;
import com.user_service.modal.CustomUserDetails;
import com.user_service.security.JwtTokenProvider;
import com.user_service.service.LoginAttemptService;
import com.user_service.service.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTests {
    private final UserEventPublisher eventPublisher = mock(UserEventPublisher.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    private final AuthController controller = new AuthController(
            eventPublisher, authenticationManager, tokenProvider, loginAttempts);

    @Test
    void authenticateUser_validCredentials_resetsAttemptsAndReturnsToken() {
        CustomUserDetails principal = principal();
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(tokenProvider.getExpirationInSeconds()).thenReturn(3600L);

        var response = controller.authenticateUser(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getBody().getExpiresIn()).isEqualTo(3600L);
        verify(loginAttempts).checkAllowed("person@example.com");
        verify(loginAttempts).recordSuccess("person@example.com");
        verify(eventPublisher).publish("LOGIN", "SUCCESS", "User logged in", 7L,
                "Person", "person@example.com", "USER");
    }

    @Test
    void authenticateUser_badCredentials_recordsFailureAndPublishesGenericFailure() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("sensitive auth detail"));

        assertThatThrownBy(() -> controller.authenticateUser(request()))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttempts).recordFailure("person@example.com");
        verify(eventPublisher).publish("LOGIN", "FAILURE", "Invalid credentials",
                null, null, "person@example.com", null);
    }

    @Test
    void authenticateUser_rateLimited_doesNotAuthenticate() {
        doThrow(new com.user_service.exception.TooManyAttemptsException(java.time.Duration.ofSeconds(30)))
                .when(loginAttempts).checkAllowed("person@example.com");

        assertThatThrownBy(() -> controller.authenticateUser(request()))
                .isInstanceOf(com.user_service.exception.TooManyAttemptsException.class);

        verifyNoInteractions(authenticationManager, eventPublisher);
    }

    @Test
    void logout_authenticatedPrincipal_publishesLogoutEvent() {
        CustomUserDetails principal = principal();
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        var response = controller.logoutUser(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("token remains valid");
        verify(eventPublisher).publish("LOGOUT", "SUCCESS", "User logged out", 7L,
                "Person", "person@example.com", "USER");
    }

    private AuthRequest request() {
        AuthRequest request = new AuthRequest();
        request.setEmail(" PERSON@EXAMPLE.COM ");
        request.setPassword("password123");
        return request;
    }

    private CustomUserDetails principal() {
        return CustomUserDetails.builder()
                .id(7L)
                .name("Person")
                .email("person@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}
