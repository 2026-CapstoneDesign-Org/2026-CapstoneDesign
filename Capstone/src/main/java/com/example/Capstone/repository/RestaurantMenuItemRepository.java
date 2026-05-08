package com.example.Capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.RestaurantMenuItem;

@Repository
public interface RestaurantMenuItemRepository extends JpaRepository<RestaurantMenuItem, Long> {
    void deleteAllByRestaurantId(Long restaurantId);

    List<RestaurantMenuItem> findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(Long restaurantId);
}
