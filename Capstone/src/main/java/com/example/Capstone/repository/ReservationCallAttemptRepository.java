package com.example.Capstone.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ReservationCallAttempt;
import com.example.Capstone.domain.ReservationCallAttemptStatus;

@Repository
public interface ReservationCallAttemptRepository extends JpaRepository<ReservationCallAttempt, Long> {

    Optional<ReservationCallAttempt> findByReservationIdAndProviderAndProviderCallId(
            Long reservationId,
            String provider,
            String providerCallId
    );

    Optional<ReservationCallAttempt> findTopByReservationIdOrderByAttemptNumberDesc(Long reservationId);

    List<ReservationCallAttempt> findByStatusAndNextRetryAtLessThanEqual(
            ReservationCallAttemptStatus status,
            LocalDateTime nextRetryAt
    );

    List<ReservationCallAttempt> findByStatusInAndStartedAtBefore(
            Collection<ReservationCallAttemptStatus> statuses,
            LocalDateTime startedAt
    );
}
