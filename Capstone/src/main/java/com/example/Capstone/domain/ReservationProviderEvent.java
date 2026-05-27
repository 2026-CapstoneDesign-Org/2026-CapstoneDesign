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
        name = "reservation_provider_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reservation_provider_events_idempotency",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_reservation_provider_events_reservation_occurred",
                        columnList = "reservation_id, occurred_at, id"
                ),
                @Index(
                        name = "idx_reservation_provider_events_provider_call",
                        columnList = "provider, provider_call_id"
                ),
                @Index(
                        name = "idx_reservation_provider_events_processing",
                        columnList = "processing_status, received_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationProviderEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private RestaurantReservation reservation;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(length = 100)
    private String providerCallId;

    @Column(length = 100)
    private String providerEventId;

    @Column(nullable = false, length = 300)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private ReservationProviderEventType eventType;

    @Column(length = 100)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReservationStatus targetStatus;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private boolean retryable;

    @Column
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReservationProviderEventProcessingStatus processingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_event_id")
    private ReservationProviderEvent duplicateOfEvent;

    @Column(length = 100)
    private String rawPayloadHash;

    @Column(columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false)
    private boolean signatureVerified;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(length = 1000)
    private String resultMessage;

    public static ReservationProviderEvent received(
            RestaurantReservation reservation,
            String provider,
            String providerCallId,
            String providerEventId,
            String idempotencyKey,
            ReservationProviderEventType eventType,
            String providerStatus,
            ReservationStatus targetStatus,
            String failureCode,
            String failureReason,
            boolean retryable,
            LocalDateTime occurredAt,
            LocalDateTime receivedAt,
            String rawPayloadHash,
            String rawPayload,
            boolean signatureVerified,
            String aiSummary,
            String resultMessage
    ) {
        ReservationProviderEvent event = new ReservationProviderEvent();
        event.reservation = reservation;
        event.provider = normalize(provider);
        event.providerCallId = normalize(providerCallId);
        event.providerEventId = normalize(providerEventId);
        event.idempotencyKey = normalize(idempotencyKey);
        event.eventType = eventType;
        event.providerStatus = normalize(providerStatus);
        event.targetStatus = targetStatus;
        event.failureCode = normalize(failureCode);
        event.failureReason = normalize(failureReason);
        event.retryable = retryable;
        event.occurredAt = occurredAt;
        event.receivedAt = receivedAt == null ? LocalDateTime.now() : receivedAt;
        event.processingStatus = ReservationProviderEventProcessingStatus.RECEIVED;
        event.rawPayloadHash = normalize(rawPayloadHash);
        event.rawPayload = normalize(rawPayload);
        event.signatureVerified = signatureVerified;
        event.aiSummary = normalize(aiSummary);
        event.resultMessage = normalize(resultMessage);
        return event;
    }

    public void markProcessed() {
        this.processingStatus = ReservationProviderEventProcessingStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void overrideTarget(ReservationStatus targetStatus, String failureReason) {
        this.targetStatus = targetStatus;
        if (failureReason != null && !failureReason.isBlank()) {
            this.failureReason = failureReason.trim();
        }
    }

    public void markIgnoredDuplicate(ReservationProviderEvent duplicateOfEvent) {
        this.processingStatus = ReservationProviderEventProcessingStatus.IGNORED_DUPLICATE;
        this.duplicateOfEvent = duplicateOfEvent;
        this.processedAt = LocalDateTime.now();
    }

    public void markIgnoredStale() {
        this.processingStatus = ReservationProviderEventProcessingStatus.IGNORED_STALE;
        this.processedAt = LocalDateTime.now();
    }

    public void markRejectedSecurity(String failureReason) {
        this.processingStatus = ReservationProviderEventProcessingStatus.REJECTED_SECURITY;
        this.failureReason = normalize(failureReason);
        this.processedAt = LocalDateTime.now();
    }

    public void markRejectedInvalid(String failureReason) {
        this.processingStatus = ReservationProviderEventProcessingStatus.REJECTED_INVALID;
        this.failureReason = normalize(failureReason);
        this.processedAt = LocalDateTime.now();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
