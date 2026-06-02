package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByRestaurantIdAndIsDeletedFalseAndIsHiddenFalse(Long restaurantId);
    List<Review> findAllByUserIdAndIsDeletedFalseAndIsHiddenFalse(Long userId);
    Optional<Review> findByIdAndIsDeletedFalse(Long id);
    long countByUserIdAndIsDeletedFalse(Long userId);

    @Query("SELECT r FROM Review r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.restaurant " +
           "LEFT JOIN FETCH r.images " +
           "WHERE r.restaurant.id = :restaurantId " +
           "AND r.isDeleted = false " +
           "AND r.isHidden = false")
    List<Review> findAllByRestaurantIdWithDetails(@Param("restaurantId") Long restaurantId);

    @Query("SELECT r FROM Review r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.restaurant " +
           "LEFT JOIN FETCH r.images " +
           "WHERE r.user.id = :userId " +
           "AND r.isDeleted = false " +
           "AND r.isHidden = false")
    List<Review> findAllByUserIdWithDetails(@Param("userId") Long userId);
}