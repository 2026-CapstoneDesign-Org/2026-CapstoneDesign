package com.example.Capstone.dto.response;

import java.math.BigDecimal;

public record HiddenGemRestaurantItemResponse(
        int rank,
        Long restaurantId,
        String restaurantName,
        String address,
        String regionName,
        String regionTownName,
        BigDecimal recommendationScore,
        BigDecimal adjustedScore,
        BigDecimal averageAutoScore,
        long evaluationCount
) {
}
