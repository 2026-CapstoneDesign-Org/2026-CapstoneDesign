package com.example.Capstone.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.RestaurantTag;

@Repository
public interface RestaurantTagRepository extends JpaRepository<RestaurantTag, Long> {
    void deleteAllByRestaurantId(Long restaurantId);
    boolean existsByTagId(Long tagId);
}
