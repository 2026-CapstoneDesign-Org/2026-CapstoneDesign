package com.example.Capstone.recommendation.model.list;

import java.util.*;

public record ListRecommendationUserProfile(
        Set<Long> ownedRestaurantIds,
        Map<Long, Double> restaurantBestScore,
        Map<String, Double> categoryPreference,
        Map<String, Double> regionPreference,
        ScoreVector scorePreference,
        String dominantRegion
) {
    public static ListRecommendationUserProfile empty() {
        return new ListRecommendationUserProfile(
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                ScoreVector.zero(),
                null
        );
    }
}
