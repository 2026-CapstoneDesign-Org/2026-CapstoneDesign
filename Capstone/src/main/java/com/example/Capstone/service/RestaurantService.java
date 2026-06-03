package com.example.Capstone.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.PageResponse;
import com.example.Capstone.dto.response.RestaurantDetailResponse;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.repository.RestaurantMenuItemRepository;
import com.example.Capstone.repository.RestaurantPhotoRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantTagRepository;
import com.example.Capstone.service.support.RestaurantBusinessHoursResolver;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private static final int DETAIL_PARKING_LOT_LIMIT = 10;

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final RestaurantTagRepository restaurantTagRepository;
    private final RestaurantPhotoRepository restaurantPhotoRepository;
    private final ParkingLotService parkingLotService;
    private final RestaurantBusinessHoursResolver restaurantBusinessHoursResolver;

    public PageResponse<RestaurantResponse> searchRestaurants(String keyword, Pageable pageable) {
        Page<RestaurantResponse> page = restaurantRepository
                .findByNameContainingAndIsDeletedFalseAndIsHiddenFalse(keyword, pageable)
                .map(RestaurantResponse::from);
        return PageResponse.from(page);
    }

    @Cacheable(value = "restaurant", key = "#id")
    public RestaurantDetailResponse getRestaurant(Long id) {
        Restaurant restaurant = findVisibleRestaurant(id);
        var businessHours = restaurantBusinessHoursResolver.parse(restaurant.getBusinessHoursRaw());
        var currentBusinessStatus = restaurantBusinessHoursResolver.resolveCurrentStatus(businessHours);
        return RestaurantDetailResponse.from(
                restaurant,
                restaurantMenuItemRepository.findAllByRestaurantIdOrderByDisplayOrderAscIdAsc(id),
                restaurantTagRepository.findActiveTagsByRestaurantId(id),
                restaurantPhotoRepository.findTop10ByRestaurantIdOrderByDisplayOrderAscIdAsc(id),
                parkingLotService.getParkingLotsForRestaurantDetail(restaurant, DETAIL_PARKING_LOT_LIMIT),
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
