package com.example.Capstone.repository;

import com.example.Capstone.domain.ReliabilityScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReliabilityScoreRepository extends JpaRepository<ReliabilityScore, Long> {
    Optional<ReliabilityScore> findByUserId(Long userId);
    List<ReliabilityScore> findAllByOrderByScoreDesc();
}
