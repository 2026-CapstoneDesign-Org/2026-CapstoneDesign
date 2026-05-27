package com.example.Capstone.client.reservation;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReservationRetryPolicy {

    private final int maxAttempts;
    private final Duration firstRetryDelay;
    private final Duration secondRetryDelay;
    private final Duration callTimeout;

    public ReservationRetryPolicy(
            @Value("${reservation.call-attempt.max-attempts:3}") int maxAttempts,
            @Value("${reservation.call-attempt.first-retry-delay-seconds:60}") long firstRetryDelaySeconds,
            @Value("${reservation.call-attempt.second-retry-delay-seconds:300}") long secondRetryDelaySeconds,
            @Value("${reservation.call-attempt.call-timeout-seconds:600}") long callTimeoutSeconds
    ) {
        this.maxAttempts = maxAttempts;
        this.firstRetryDelay = Duration.ofSeconds(firstRetryDelaySeconds);
        this.secondRetryDelay = Duration.ofSeconds(secondRetryDelaySeconds);
        this.callTimeout = Duration.ofSeconds(callTimeoutSeconds);
    }

    public boolean canRetryAfterAttempt(int attemptNumber) {
        return attemptNumber < maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public LocalDateTime nextRetryAt(int attemptNumber, LocalDateTime failedAt) {
        LocalDateTime baseTime = failedAt == null ? LocalDateTime.now() : failedAt;
        Duration delay = attemptNumber <= 1 ? firstRetryDelay : secondRetryDelay;
        return baseTime.plus(delay);
    }

    public LocalDateTime timeoutThreshold(LocalDateTime referenceTime) {
        LocalDateTime baseTime = referenceTime == null ? LocalDateTime.now() : referenceTime;
        return baseTime.minus(callTimeout);
    }
}
