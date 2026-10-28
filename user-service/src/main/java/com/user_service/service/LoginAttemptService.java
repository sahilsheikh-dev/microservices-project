package com.user_service.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.user_service.exception.TooManyAttemptsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(15);
    private final Cache<String, FailureWindow> failures;
    private final Ticker ticker;

    public LoginAttemptService() {
        this(Ticker.systemTicker());
    }

    LoginAttemptService(Ticker ticker) {
        this.ticker = ticker;
        this.failures = Caffeine.newBuilder().maximumSize(10_000).ticker(ticker).build();
    }

    public void checkAllowed(String email) {
        String key = normalize(email);
        FailureWindow window = failures.getIfPresent(key);
        if (window == null) return;
        long remaining = WINDOW_NANOS - (ticker.read() - window.startedAtNanos());
        if (remaining <= 0) {
            failures.invalidate(key);
        } else if (window.count() >= MAX_FAILURES) {
            throw new TooManyAttemptsException(Duration.ofNanos(remaining));
        }
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        long now = ticker.read();
        failures.asMap().compute(key, (ignored, current) -> current == null
                || now - current.startedAtNanos() >= WINDOW_NANOS
                ? new FailureWindow(1, now)
                : new FailureWindow(current.count() + 1, current.startedAtNanos()));
    }

    public void recordSuccess(String email) {
        failures.invalidate(normalize(email));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record FailureWindow(int count, long startedAtNanos) {
    }
}
