package com.journal_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notFound_resourceNotFound_returns404Mapping() {
        assertMapping(handler.notFound(new ResourceNotFoundException("missing"), request()), 404, "NOT_FOUND");
    }

    @Test
    void conflict_domainAndDatabaseConflicts_return409Mapping() {
        assertMapping(handler.conflict(new ConflictException("duplicate"), request()), 409, "CONFLICT");
        assertMapping(handler.dataConflict(request()), 409, "CONFLICT");
    }

    @Test
    void accessDenied_anonymousAndAuthenticated_return401And403() {
        SecurityContextHolder.clearContext();
        assertMapping(handler.accessDenied(new AccessDeniedException("denied"), request()), 401, "UNAUTHORIZED");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a"));
        assertMapping(handler.accessDenied(new AccessDeniedException("denied"), request()), 403, "FORBIDDEN");
    }

    @Test
    void authenticationFailed_unknownEmailAndWrongPassword_share401Message() {
        String unknownEmailMessage = body(handler.authenticationFailed(request())).message();
        String wrongPasswordMessage = body(handler.authenticationFailed(request())).message();

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage)
                .isEqualTo("Invalid email or password.");
    }

    @Test
    void invalidArgument_badInput_returns400Mapping() {
        assertMapping(handler.invalidArgument(new IllegalArgumentException("bad input"), request()),
                400, "BAD_REQUEST");
    }

    @Test
    void unexpected_internalFailure_returnsGeneric500Message() {
        ApiErrorResponse response = body(handler.unexpected(new IllegalStateException("private detail"), request()));

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.message()).isEqualTo("An unexpected error occurred.");
        assertThat(response.message()).doesNotContain("private detail");
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/test");
        return request;
    }

    private void assertMapping(ResponseEntity<Object> response, int status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(status));
        assertThat(body(response).code()).isEqualTo(code);
    }

    private ApiErrorResponse body(ResponseEntity<Object> response) {
        return (ApiErrorResponse) response.getBody();
    }
}
