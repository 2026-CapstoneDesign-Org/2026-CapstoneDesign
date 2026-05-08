package com.example.Capstone.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.RestaurantPhoto;

@Repository
public interface RestaurantPhotoRepository extends JpaRepository<RestaurantPhoto, Long> {
    void deleteAllByRestaurantId(Long restaurantId);

    List<RestaurantPhoto> findTop10ByRestaurantIdOrderByDisplayOrderAscIdAsc(Long restaurantId);
}
