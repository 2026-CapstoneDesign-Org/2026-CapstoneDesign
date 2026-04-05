package com.example.Capstone.dto.response;

import java.math.BigDecimal;

import com.example.Capstone.domain.ListRestaurant;

public record ListRestaurantResponse(
        Long id,
        RestaurantResponse restaurant,
        BigDecimal tasteScore,
        BigDecimal valueScore,
        BigDecimal moodScore,
        BigDecimal autoScore
) {
    public static ListRestaurantResponse from(ListRestaurant listRestaurant) {
        return new ListRestaurantResponse(
                listRestaurant.getId(),
                RestaurantResponse.from(listRestaurant.getRestaurant()),
                listRestaurant.getTasteScore(),
                listRestaurant.getValueScore(),
                listRestaurant.getMoodScore(),
                listRestaurant.getAutoScore()
        );
    }
}
