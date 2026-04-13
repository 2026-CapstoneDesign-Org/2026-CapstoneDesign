package com.example.Capstone.recommendation.model.list;

import java.util.*;

public record ListRecommendationFeature(
        Long listId,
        String title,
        String description,
        String regionName,
        OwnerFeature owner,
        int restaurantCount,
        Map<Long, Double> restaurantWeight,
        Map<String, Double> categoryDistribution,
        ScoreVector scoreVector,
        double averageAutoScore,
        double adjustedQualityScore,
        List<String> categorySummary
) {}
