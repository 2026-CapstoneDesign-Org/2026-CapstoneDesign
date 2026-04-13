package com.example.Capstone.recommendation.scorer;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Capstone.recommendation.model.list.ListRecommendationFeature;
import com.example.Capstone.recommendation.model.list.ListRecommendationScoreComponents;
import com.example.Capstone.recommendation.model.list.ListRecommendationUserProfile;
import com.example.Capstone.recommendation.model.list.ScoreVector;

@Component
public class ListRecommendationScorer {

    static final double PREFERENCE_MATCH_WEIGHT = 0.55;
    static final double QUALITY_WEIGHT = 0.20;
    static final double COLLABORATIVE_WEIGHT = 0.10;
    static final double REGION_WEIGHT = 0.15;

    static final double RESTAURANT_MATCH_WEIGHT = 0.45;
    static final double CATEGORY_MATCH_WEIGHT = 0.35;
    static final double SCORE_STYLE_MATCH_WEIGHT = 0.20;

    static final double ADJUSTED_QUALITY_WEIGHT = 0.75;
    static final double SIZE_CONFIDENCE_WEIGHT = 0.25;

    static final double ZERO_SIMILARITY_PENALTY = 0.10;
    static final double REDUCED_COLLABORATIVE_MULTIPLIER = 0.5;

    public ListRecommendationScoreComponents score(
            ListRecommendationUserProfile profile,
            ListRecommendationFeature candidate,
            double collaborativeScore,
            double regionScore
    ) {
        double restaurantMatchScore = calculateRestaurantMatchScore(
                profile.restaurantBestScore(),
                candidate.restaurantWeight()
        );
        double categoryMatchScore = calculateCategoryMatchScore(
                profile.categoryPreference(),
                candidate.categoryDistribution()
        );
        double scoreStyleMatchScore = cosineSimilarity(profile.scorePreference(), candidate.scoreVector());

        double preferenceMatchScore = clamp(
                RESTAURANT_MATCH_WEIGHT * restaurantMatchScore
                        + CATEGORY_MATCH_WEIGHT * categoryMatchScore
                        + SCORE_STYLE_MATCH_WEIGHT * scoreStyleMatchScore
        );

        double adjustedQualityScore = clamp(candidate.adjustedQualityScore());
        double sizeConfidenceScore = calculateSizeConfidenceScore(candidate.restaurantCount());
        double qualityScore = clamp(
                ADJUSTED_QUALITY_WEIGHT * adjustedQualityScore
                        + SIZE_CONFIDENCE_WEIGHT * sizeConfidenceScore
        );

        boolean zeroSimilarityCandidate = restaurantMatchScore <= 0.0 && categoryMatchScore <= 0.0;
        double normalizedCollaborativeScore = clamp(collaborativeScore);
        if (zeroSimilarityCandidate) {
            normalizedCollaborativeScore = clamp(normalizedCollaborativeScore * REDUCED_COLLABORATIVE_MULTIPLIER);
        }

        double normalizedRegionScore = clamp(regionScore);
        double finalScore = PREFERENCE_MATCH_WEIGHT * preferenceMatchScore
                + QUALITY_WEIGHT * qualityScore
                + COLLABORATIVE_WEIGHT * normalizedCollaborativeScore
                + REGION_WEIGHT * normalizedRegionScore;

        if (preferenceMatchScore <= 0.0) {
            finalScore -= ZERO_SIMILARITY_PENALTY;
        }

        return new ListRecommendationScoreComponents(
                clamp(finalScore),
                preferenceMatchScore,
                restaurantMatchScore,
                categoryMatchScore,
                scoreStyleMatchScore,
                qualityScore,
                adjustedQualityScore,
                sizeConfidenceScore,
                normalizedCollaborativeScore,
                normalizedRegionScore
        );
    }

    private double calculateRestaurantMatchScore(
            Map<Long, Double> userRestaurantBestScore,
            Map<Long, Double> candidateRestaurantWeight
    ) {
        if (userRestaurantBestScore == null || userRestaurantBestScore.isEmpty()
                || candidateRestaurantWeight == null || candidateRestaurantWeight.isEmpty()) {
            return 0.0;
        }

        double weightedScore = 0.0;
        for (Map.Entry<Long, Double> entry : candidateRestaurantWeight.entrySet()) {
            weightedScore += clamp(entry.getValue()) * clamp(userRestaurantBestScore.getOrDefault(entry.getKey(), 0.0));
        }
        return clamp(weightedScore);
    }

    private double calculateCategoryMatchScore(
            Map<String, Double> userCategoryPreference,
            Map<String, Double> candidateCategoryDistribution
    ) {
        if (userCategoryPreference == null || userCategoryPreference.isEmpty()
                || candidateCategoryDistribution == null || candidateCategoryDistribution.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        for (Map.Entry<String, Double> entry : candidateCategoryDistribution.entrySet()) {
            score += clamp(entry.getValue()) * clamp(userCategoryPreference.getOrDefault(entry.getKey(), 0.0));
        }
        return clamp(score);
    }

    private double calculateSizeConfidenceScore(int restaurantCount) {
        if (restaurantCount <= 0) {
            return 0.0;
        }
        return clamp(restaurantCount / 10.0);
    }

    private double cosineSimilarity(ScoreVector left, ScoreVector right) {
        if (left == null || right == null) {
            return 0.0;
        }

        double numerator = left.taste() * right.taste()
                + left.value() * right.value()
                + left.mood() * right.mood();
        double leftNorm = left.taste() * left.taste()
                + left.value() * left.value()
                + left.mood() * left.mood();
        double rightNorm = right.taste() * right.taste()
                + right.value() * right.value()
                + right.mood() * right.mood();

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }

        return clamp(numerator / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
