package com.example.Capstone.recommendation.model.restaurant;

import java.util.Map;

public record NeighborProfile(
        Long userId,
        double similarity,
        Map<Long, Double> restaurantBestScore
) {}
