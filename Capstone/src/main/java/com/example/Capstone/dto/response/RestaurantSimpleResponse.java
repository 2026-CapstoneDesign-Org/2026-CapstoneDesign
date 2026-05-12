package com.example.Capstone.dto.response;

import com.example.Capstone.domain.Restaurant;

public record RestaurantSimpleResponse(
        Long id,
        String name,
        String address,
        String regionName,
        String imageUrl
) {
    public static RestaurantSimpleResponse from(Restaurant restaurant) {
        return new RestaurantSimpleResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getRegionName(),
                restaurant.getImageUrl()
        );
    }
}

