package com.example.Capstone.recommendation.model.restaurant;

import java.time.LocalDateTime;
import java.util.Set;

public record LikedRestaurantFeature(
        Long restaurantId,
        double score,
        String regionName,
        Set<String> categories,
        LocalDateTime updatedAt
) {}
