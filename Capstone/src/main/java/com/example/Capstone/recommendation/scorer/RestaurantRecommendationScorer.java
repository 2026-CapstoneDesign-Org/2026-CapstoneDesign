package com.example.Capstone.recommendation.scorer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.Capstone.recommendation.model.restaurant.LikedRestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.RecommendationScoreComponents;
import com.example.Capstone.recommendation.model.restaurant.RestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.UserPreferenceProfile;

@Component
public class  RestaurantRecommendationScorer {

    static final double USER_PREFERENCE_WEIGHT = 0.45;
    static final double CATEGORY_FIT_WEIGHT = 0.20;
    static final double RANKING_ADJUSTMENT_WEIGHT = 0.20;
    static final double COLLABORATIVE_WEIGHT = 0.05;
    static final double REGION_WEIGHT = 0.10;
    static final double ZERO_SIMILARITY_PENALTY = 0.25;
    static final double COLLABORATIVE_REDUCTION_WITHOUT_PREFERENCE = 0.5;

    public RecommendationScoreComponents score(
            UserPreferenceProfile profile,
            RestaurantFeature candidate,
            double collaborativeScore,
            double regionScore
    ) {
        double userPreferenceScore = calculateUserPreferenceScore(profile.topLikedRestaurants(), candidate.categories());
        double categoryFitScore = calculateCategoryFitScore(profile.categoryPreference(), candidate.categories());
        double rankingAdjustmentScore = clamp(candidate.rankingAdjustmentScore());
        double normalizedCollaborativeScore = adjustCollaborativeScore(
                clamp(collaborativeScore),
                userPreferenceScore
        );
        double normalizedRegionScore = clamp(regionScore);

        double finalScore = USER_PREFERENCE_WEIGHT * userPreferenceScore
                + CATEGORY_FIT_WEIGHT * categoryFitScore
                + RANKING_ADJUSTMENT_WEIGHT * rankingAdjustmentScore
                + COLLABORATIVE_WEIGHT * normalizedCollaborativeScore
                + REGION_WEIGHT * normalizedRegionScore;

        if (isZeroSimilarityCandidate(userPreferenceScore, categoryFitScore)) {
            finalScore -= ZERO_SIMILARITY_PENALTY;
        }

        return new RecommendationScoreComponents(
                clamp(finalScore),
                userPreferenceScore,
                categoryFitScore,
                rankingAdjustmentScore,
                normalizedCollaborativeScore,
                normalizedRegionScore
        );
    }

    private double adjustCollaborativeScore(double collaborativeScore, double userPreferenceScore) {
        if (userPreferenceScore <= 0.0) {
            return clamp(collaborativeScore * COLLABORATIVE_REDUCTION_WITHOUT_PREFERENCE);
        }
        return collaborativeScore;
    }

    private boolean isZeroSimilarityCandidate(double userPreferenceScore, double categoryFitScore) {
        return userPreferenceScore <= 0.0 && categoryFitScore <= 0.0;
    }

    private double calculateUserPreferenceScore(
            List<LikedRestaurantFeature> topLikedRestaurants,
            List<String> candidateCategories
    ) {
        if (topLikedRestaurants == null || topLikedRestaurants.isEmpty()) {
            return 0.0;
        }

        Set<String> candidateCategorySet = toCategorySet(candidateCategories);
        double numerator = 0.0;
        double denominator = 0.0;

        for (LikedRestaurantFeature likedRestaurant : topLikedRestaurants) {
            numerator += likedRestaurant.score() * jaccardSimilarity(candidateCategorySet, likedRestaurant.categories());
            denominator += likedRestaurant.score();
        }

        if (denominator == 0.0) {
            return 0.0;
        }

        return clamp(numerator / denominator);
    }

    private double calculateCategoryFitScore(Map<String, Double> categoryPreference, List<String> candidateCategories) {
        if (categoryPreference == null || categoryPreference.isEmpty()
                || candidateCategories == null || candidateCategories.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;
        for (String category : candidateCategories) {
            sum += categoryPreference.getOrDefault(category, 0.0);
            count++;
        }

        if (count == 0) {
            return 0.0;
        }
        return clamp(sum / count);
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);

        Set<String> union = new HashSet<>(left);
        union.addAll(right);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    private Set<String> toCategorySet(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(categories);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
