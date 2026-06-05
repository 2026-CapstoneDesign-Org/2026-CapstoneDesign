package com.example.Capstone.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.dto.request.CreateRestaurantRequest;
import com.example.Capstone.dto.request.UpdateCategoryRequest;
import com.example.Capstone.dto.request.UpdateRestaurantRequest;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.service.admin.AdminRestaurantCommandService;
import com.example.Capstone.service.admin.AdminVisibilityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRestaurantCommandService adminRestaurantCommandService;
    private final AdminVisibilityService adminVisibilityService;

    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        return adminRestaurantCommandService.createRestaurant(request);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long restaurantId, UpdateRestaurantRequest request) {
        return adminRestaurantCommandService.updateRestaurant(restaurantId, request);
    }

    @Transactional
    public void updateCategories(Long restaurantId, UpdateCategoryRequest request) {
        adminRestaurantCommandService.updateCategories(restaurantId, request);
    }

    @Transactional
    public void hideRestaurant(Long restaurantId) {
        adminRestaurantCommandService.hideRestaurant(restaurantId);
    }

    @Transactional
    public void hideUser(Long userId) {
        adminVisibilityService.hideUser(userId);
    }

    @Transactional
    public void hideList(Long listId) {
        adminVisibilityService.hideList(listId);
    }
}
