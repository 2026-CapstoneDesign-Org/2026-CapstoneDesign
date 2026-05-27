package com.example.Capstone.domain;

import java.time.LocalDateTime;

import com.example.Capstone.domain.base.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "reservation_call_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reservation_call_attempts_provider_call",
                        columnNames = {"reservation_id", "provider", "provider_call_id"}
                ),
                @UniqueConstraint(
                        name = "uq_reservation_call_attempts_attempt_number",
                        columnNames = {"reservation_id", "attempt_number"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_reservation_call_attempts_retry",
                        columnList = "status, next_retry_at"
                ),
                @Index(
                        name = "idx_reservation_call_attempts_timeout",
                        columnList = "status, started_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationCallAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private RestaurantReservation reservation;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 100)
    private String providerCallId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReservationCallAttemptStatus status;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    @Column
    private LocalDateTime nextRetryAt;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 100)
    private String lastProviderEventId;

    public static ReservationCallAttempt started(
            RestaurantReservation reservation,
            Integer attemptNumber,
            String provider,
            String providerCallId,
            LocalDateTime startedAt,
            String providerEventId
    ) {
        ReservationCallAttempt attempt = new ReservationCallAttempt();
        attempt.reservation = reservation;
        attempt.attemptNumber = attemptNumber;
        attempt.provider = normalize(provider);
        attempt.providerCallId = normalize(providerCallId);
        attempt.status = ReservationCallAttemptStatus.STARTED;
        attempt.startedAt = startedAt == null ? LocalDateTime.now() : startedAt;
        attempt.lastProviderEventId = normalize(providerEventId);
        return attempt;
    }

    public void markConnected(String providerEventId) {
        if (status == ReservationCallAttemptStatus.STARTED) {
            this.status = ReservationCallAttemptStatus.CONNECTED;
        }
        this.lastProviderEventId = normalize(providerEventId);
    }

    public void markRetryScheduled(
            String failureCode,
            String failureReason,
            LocalDateTime nextRetryAt,
            String providerEventId
    ) {
        this.status = ReservationCallAttemptStatus.RETRY_SCHEDULED;
        this.endedAt = LocalDateTime.now();
        this.failureCode = normalize(failureCode);
        this.failureReason = normalize(failureReason);
        this.nextRetryAt = nextRetryAt;
        this.lastProviderEventId = normalize(providerEventId);
    }

    public void markRetryDispatched(String providerEventId) {
        if (status == ReservationCallAttemptStatus.RETRY_SCHEDULED) {
            this.status = ReservationCallAttemptStatus.RETRY_DISPATCHED;
            this.nextRetryAt = null;
        }
        this.lastProviderEventId = normalize(providerEventId);
    }

    public void markCompleted(String providerEventId) {
        this.status = ReservationCallAttemptStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.nextRetryAt = null;
        this.lastProviderEventId = normalize(providerEventId);
    }

    public void markFinalFailure(String failureCode, String failureReason, String providerEventId) {
        this.status = ReservationCallAttemptStatus.FAILED_FINAL;
        this.endedAt = LocalDateTime.now();
        this.nextRetryAt = null;
        this.failureCode = normalize(failureCode);
        this.failureReason = normalize(failureReason);
        this.lastProviderEventId = normalize(providerEventId);
    }

    public boolean isRetryReady() {
        return status == ReservationCallAttemptStatus.RETRY_SCHEDULED;
    }

    public boolean isTimeoutCandidateStatus() {
        return status == ReservationCallAttemptStatus.STARTED
                || status == ReservationCallAttemptStatus.CONNECTED;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
