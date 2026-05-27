package com.example.Capstone.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ReservationProviderEvent;

@Repository
public interface ReservationProviderEventRepository extends JpaRepository<ReservationProviderEvent, Long> {

    Optional<ReservationProviderEvent> findByIdempotencyKey(String idempotencyKey);
}
