package com.example.Capstone.recommendation.model.restaurant;

public record RecommendationScoreComponents(
        double finalScore,
        double userPreferenceScore,
        double categoryFitScore,
        double rankingAdjustmentScore,
        double collaborativeScore,
        double regionScore
) {}
