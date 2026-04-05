package com.example.Capstone.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantCategory;

public record RestaurantResponse(
        Long id,
        String name,
        String address,
        String regionName,
        BigDecimal lat,
        BigDecimal lng,
        String imageUrl,
        List<String> categories
) {
    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getRegionName(),
                restaurant.getLat(),
                restaurant.getLng(),
                restaurant.getImageUrl(),
                restaurant.getCategories().stream()
                        .map(RestaurantCategory::getCategoryName)
                        .toList()
        );
    }
}
