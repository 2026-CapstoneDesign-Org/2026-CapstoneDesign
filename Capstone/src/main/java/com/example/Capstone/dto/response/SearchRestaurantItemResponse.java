package com.example.Capstone.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SearchRestaurantItemResponse(
        Long restaurantId,
        String externalPlaceId,
        String source,
        String restaurantName,
        String regionName,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String imageUrl,
        String primaryCategoryName,
        List<String> categories,
        String matchedBy
) {
}
