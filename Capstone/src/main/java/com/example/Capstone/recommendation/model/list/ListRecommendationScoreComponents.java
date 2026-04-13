package com.example.Capstone.recommendation.model.list;

public record ListRecommendationScoreComponents(
        double finalScore,
        double preferenceMatchScore,
        double restaurantMatchScore,
        double categoryMatchScore,
        double scoreStyleMatchScore,
        double qualityScore,
        double adjustedQualityScore,
        double sizeConfidenceScore,
        double collaborativeScore,
        double regionScore
) {}
