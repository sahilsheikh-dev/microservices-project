package com.journal_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> conflict(ConflictException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> dataConflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpStatus status = authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return response(status, status.name(), status.getReasonPhrase(), request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> authenticationFailed(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password.", request, List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> invalidArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.", request, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ApiErrorResponse error = body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request validation failed.",
                servletRequest(request).getRequestURI(), fields);
        return new ResponseEntity<>(error, headers, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.valueOf(status.value());
        String message = resolved.is4xxClientError() && ex.getMessage() != null
                ? ex.getMessage() : resolved.getReasonPhrase();
        ApiErrorResponse error = body(resolved, resolved.name(), message,
                servletRequest(request).getRequestURI(), List.of());
        return new ResponseEntity<>(error, headers, status);
    }

    private ResponseEntity<Object> response(HttpStatus status, String code, String message,
                                            HttpServletRequest request,
                                            List<ApiErrorResponse.FieldError> fields) {
        return ResponseEntity.status(status).body(body(status, code, message, request.getRequestURI(), fields));
    }

    private ApiErrorResponse body(HttpStatus status, String code, String message, String path,
                                  List<ApiErrorResponse.FieldError> fields) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code,
                message, path, fields);
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }
}
