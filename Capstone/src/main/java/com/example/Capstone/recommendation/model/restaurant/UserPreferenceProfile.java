package com.example.Capstone.recommendation.model.restaurant;

import java.util.*;

public record UserPreferenceProfile(
        Set<Long> ownedRestaurantIds,
        Map<Long, Double> restaurantBestScore,
        Map<String, Double> categoryPreference,
        Map<String, Double> regionPreference,
        List<LikedRestaurantFeature> topLikedRestaurants,
        String dominantRegion
) {
    public static UserPreferenceProfile empty() {
        return new UserPreferenceProfile(
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                null
        );
    }
}








