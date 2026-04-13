package com.example.Capstone.recommendation.model.restaurant;

import java.util.List;

public record RestaurantFeature(
        Long restaurantId,
        String restaurantName,
        String address,
        String regionName,
        String imageUrl,
        List<String> categories,
        double rankingAdjustmentScore,
        long evaluationCount
) {}
