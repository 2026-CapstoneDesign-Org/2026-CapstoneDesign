package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(Long restaurantId);
    Optional<Review> findByIdAndIsDeletedFalse(Long id);
}
