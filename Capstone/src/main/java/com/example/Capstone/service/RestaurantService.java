package com.example.Capstone.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.RestaurantDetailResponse;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.repository.RestaurantMenuItemRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantTagRepository;
import com.example.Capstone.service.support.RestaurantBusinessHoursResolver;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final RestaurantTagRepository restaurantTagRepository;
    private final RestaurantBusinessHoursResolver restaurantBusinessHoursResolver;

    public List<RestaurantResponse> searchRestaurants(String keyword) {
        return restaurantRepository.findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(keyword).stream()
                .map(RestaurantResponse::from)
                .toList();
    }

    public RestaurantDetailResponse getRestaurant(Long id) {
        Restaurant restaurant = findVisibleRestaurant(id);
        var businessHours = restaurantBusinessHoursResolver.parse(restaurant.getBusinessHoursRaw());
        var currentBusinessStatus = restaurantBusinessHoursResolver.resolveCurrentStatus(businessHours);
        return RestaurantDetailResponse.from(
                restaurant,
                restaurantMenuItemRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(id),
                restaurantTagRepository.findActiveTagsByRestaurantId(id),
                businessHours,
                restaurantBusinessHoursResolver.resolveDisplay(businessHours, currentBusinessStatus),
                currentBusinessStatus
        );
    }

    private Restaurant findVisibleRestaurant(Long id) {
        return restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("식당을 찾을 수 없습니다."));
    }
}
