package com.example.Capstone.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.repository.RestaurantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public List<RestaurantResponse> searchRestaurants(String keyword) {
        return restaurantRepository.findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(keyword).stream()
                .map(RestaurantResponse::from)
                .toList();
    }

    public RestaurantResponse getRestaurant(Long id) {
        return RestaurantResponse.from(findVisibleRestaurant(id));
    }

    private Restaurant findVisibleRestaurant(Long id) {
        return restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
    }
}
