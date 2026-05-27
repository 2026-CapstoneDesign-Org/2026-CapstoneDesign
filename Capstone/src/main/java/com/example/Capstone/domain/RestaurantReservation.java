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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private LocalDateTime reservationDateTime;

    @Column(nullable = false)
    private Integer partySize;

    @Column(length = 500)
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(nullable = false, length = 50)
    private String restaurantPhoneNumberSnapshot;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(length = 1000)
    private String resultMessage;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 50)
    private String provider;

    @Column(length = 100)
    private String providerCallId;

    @Column(length = 100)
    private String providerStatus;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column
    private LocalDateTime lastAttemptedAt;

    @Column
    private LocalDateTime confirmedAt;

    @Column
    private LocalDateTime canceledAt;

    @Builder
    private RestaurantReservation(
            User user,
            Restaurant restaurant,
            LocalDateTime reservationDateTime,
            Integer partySize,
            String requestNote,
            String restaurantPhoneNumberSnapshot
    ) {
        this.user = user;
        this.restaurant = restaurant;
        this.reservationDateTime = reservationDateTime;
        this.partySize = partySize;
        this.requestNote = normalize(requestNote);
        this.restaurantPhoneNumberSnapshot = normalize(restaurantPhoneNumberSnapshot);
        this.status = ReservationStatus.REQUESTED;
        this.attemptCount = 0;
    }

    public void applyProviderStart(
            ReservationStatus startStatus,
            String provider,
            String providerCallId,
            String providerStatus
    ) {
        this.provider = normalize(provider);
        this.providerCallId = normalize(providerCallId);
        this.providerStatus = normalize(providerStatus);

        if (startStatus == null || startStatus == ReservationStatus.REQUESTED) {
            return;
        }

        transitionTo(startStatus);
    }

    public void applyMockResult(
            ReservationStatus nextStatus,
            String aiSummary,
            String resultMessage,
            String failureReason,
            String providerCallId
    ) {
        if (!status.canApplyMockResult(nextStatus)) {
            throw new IllegalStateException("허용되지 않는 예약 상태 전이입니다.");
        }

        if (providerCallId != null && !providerCallId.isBlank()) {
            this.providerCallId = providerCallId.trim();
        }
        this.provider = "MOCK";
        this.providerStatus = "MOCK_" + nextStatus.name();
        this.aiSummary = normalize(aiSummary);
        this.resultMessage = normalize(resultMessage);
        this.failureReason = normalize(failureReason);

        transitionTo(nextStatus);
    }

    public void applyProviderEvent(
            ReservationStatus nextStatus,
            String provider,
            String providerCallId,
            String providerStatus,
            String aiSummary,
            String resultMessage,
            String failureReason
    ) {
        this.provider = normalize(provider);
        if (providerCallId != null && !providerCallId.isBlank()) {
            this.providerCallId = providerCallId.trim();
        }
        this.providerStatus = normalize(providerStatus);
        this.aiSummary = normalize(aiSummary);
        this.resultMessage = normalize(resultMessage);
        this.failureReason = normalize(failureReason);

        if (nextStatus == null || this.status == nextStatus) {
            return;
        }

        transitionTo(nextStatus);
    }

    public void recordCallAttemptStarted(
            String provider,
            String providerCallId,
            String providerStatus,
            LocalDateTime attemptedAt
    ) {
        if (status.isTerminal() || status == ReservationStatus.NEEDS_CONFIRMATION) {
            throw new IllegalStateException("현재 상태에서는 전화 시도를 기록할 수 없습니다.");
        }

        this.provider = normalize(provider);
        if (providerCallId != null && !providerCallId.isBlank()) {
            this.providerCallId = providerCallId.trim();
        }
        this.providerStatus = normalize(providerStatus);

        if (status == ReservationStatus.REQUESTED) {
            this.status = ReservationStatus.CALLING;
        }
        this.attemptCount += 1;
        this.lastAttemptedAt = attemptedAt == null ? LocalDateTime.now() : attemptedAt;
    }

    public void cancel() {
        if (!status.isCancelable()) {
            throw new IllegalStateException("현재 상태에서는 예약을 취소할 수 없습니다.");
        }

        transitionTo(ReservationStatus.CANCELED);
    }

    private void transitionTo(ReservationStatus nextStatus) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("허용되지 않는 예약 상태 전이입니다.");
        }

        this.status = nextStatus;
        if (nextStatus == ReservationStatus.CALLING) {
            this.attemptCount += 1;
            this.lastAttemptedAt = LocalDateTime.now();
        }
        if (nextStatus == ReservationStatus.CONFIRMED) {
            this.confirmedAt = LocalDateTime.now();
        }
        if (nextStatus == ReservationStatus.CANCELED) {
            this.canceledAt = LocalDateTime.now();
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
