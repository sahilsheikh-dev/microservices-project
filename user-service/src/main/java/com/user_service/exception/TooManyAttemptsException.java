package com.user_service.exception;

import java.time.Duration;

public class TooManyAttemptsException extends RuntimeException {
    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many login attempts");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
