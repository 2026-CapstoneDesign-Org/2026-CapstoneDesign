package com.example.Capstone.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.Capstone.domain.ListRestaurant;

@Repository
public interface ListRestaurantRepository extends JpaRepository<ListRestaurant, Long> {
    Optional<ListRestaurant> findByIdAndUserListId(Long id, Long listId);
    Optional<ListRestaurant> findByUserListIdAndRestaurantId(Long listId, Long restaurantId);
    void deleteAllByUserListId(Long userListId);
}
