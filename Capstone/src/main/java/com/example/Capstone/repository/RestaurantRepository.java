package com.example.Capstone.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.Restaurant;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(String keyword);
    Optional<Restaurant> findByIdAndIsDeletedFalseAndIsHiddenFalse(Long id);
}
