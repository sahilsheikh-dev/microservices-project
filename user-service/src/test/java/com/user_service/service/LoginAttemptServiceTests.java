package com.user_service.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.user_service.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTests {
    @Test
    void checkAllowed_fiveFailures_allowsFifthAttemptAndBlocksSixthUntilWindowExpires() {
        AtomicLong time = new AtomicLong();
        Ticker ticker = new Ticker() {
            @Override
            public long read() {
                return time.get();
            }
        };
        LoginAttemptService attempts = new LoginAttemptService(ticker);
        for (int attempt = 0; attempt < 4; attempt++) {
            attempts.recordFailure(" USER@example.com ");
        }
        assertThatCode(() -> attempts.checkAllowed("user@example.com")).doesNotThrowAnyException();
        attempts.recordFailure("user@example.com");

        assertThatThrownBy(() -> attempts.checkAllowed("user@example.com"))
                .isInstanceOf(TooManyAttemptsException.class);
        time.addAndGet(TimeUnit.MINUTES.toNanos(15));
        assertThatCode(() -> attempts.checkAllowed("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void recordSuccess_fivePriorFailures_allowsNextAttempt() {
        LoginAttemptService attempts = new LoginAttemptService(new Ticker() {
            @Override
            public long read() {
                return 0L;
            }
        });
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.recordFailure("user@example.com");
        }

        attempts.recordSuccess("USER@example.com");

        assertThatCode(() -> attempts.checkAllowed("user@example.com")).doesNotThrowAnyException();
    }
}
