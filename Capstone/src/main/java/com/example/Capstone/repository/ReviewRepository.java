package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(Long restaurantId);
    List<Review> findAllByUserIdAndIsDeletedFalseAndIsHiddenFalse(Long userId);
    Optional<Review> findByIdAndIsDeletedFalse(Long id);
    Page<Review> findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(Long restaurantId, Pageable pageable);
    Page<Review> findAllByUserIdAndIsDeletedFalseAndIsHiddenFalse(Long userId, Pageable pageable);
}
