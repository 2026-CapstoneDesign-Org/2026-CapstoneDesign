package com.example.Capstone.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.RestaurantReservation;

import jakarta.persistence.LockModeType;

@Repository
public interface RestaurantReservationRepository extends JpaRepository<RestaurantReservation, Long> {

    List<RestaurantReservation> findAllByUserIdOrderByReservationDateTimeDescIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RestaurantReservation r where r.id = :id")
    Optional<RestaurantReservation> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RestaurantReservation> findByProviderAndProviderCallId(String provider, String providerCallId);

    boolean existsByUserIdAndRestaurantIdAndReservationDateTimeAndStatusIn(
            Long userId,
            Long restaurantId,
            LocalDateTime reservationDateTime,
            Collection<ReservationStatus> statuses
    );
}
