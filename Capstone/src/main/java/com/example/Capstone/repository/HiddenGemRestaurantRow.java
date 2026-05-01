package com.example.Capstone.repository;

import java.math.BigDecimal;

public record HiddenGemRestaurantRow(
        Long restaurantId,
        String restaurantName,
        String address,
        String regionName,
        String regionTownName,
        BigDecimal averageAutoScore,
        Long evaluationCount,
        BigDecimal adjustedScore
) {
}
