package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.recommendation.model.restaurant.LikedRestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.RecommendationScoreComponents;
import com.example.Capstone.recommendation.model.restaurant.RestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.UserPreferenceProfile;
import com.example.Capstone.recommendation.scorer.RestaurantRecommendationScorer;

class RestaurantRecommendationScorerTest {

    private final RestaurantRecommendationScorer scorer = new RestaurantRecommendationScorer();

    @Test
    @DisplayName("추천 점수를 고정 가중치대로 계산한다")
    void calculatesScoreUsingConfiguredWeights() {
        UserPreferenceProfile profile = new UserPreferenceProfile(
                Set.of(1L, 2L),
                Map.of(1L, 0.9, 2L, 0.8),
                Map.of("한식", 0.75, "카페", 0.25),
                Map.of("서울", 1.0),
                List.of(
                        new LikedRestaurantFeature(1L, 0.9, "서울", Set.of("한식"), LocalDateTime.now()),
                        new LikedRestaurantFeature(2L, 0.8, "서울", Set.of("카페"), LocalDateTime.now())
                ),
                "서울"
        );
        RestaurantFeature candidate = new RestaurantFeature(
                101L,
                "식당 A",
                "주소",
                "서울",
                "a.png",
                List.of("한식"),
                0.8,
                12L
        );

        RecommendationScoreComponents components = scorer.score(profile, candidate, 0.4, 1.0);

        assertEquals(0.5294, components.userPreferenceScore(), 0.0001);
        assertEquals(0.7500, components.categoryFitScore(), 0.0001);
        assertEquals(0.8000, components.rankingAdjustmentScore(), 0.0001);
        assertEquals(0.4000, components.collaborativeScore(), 0.0001);
        assertEquals(1.0000, components.regionScore(), 0.0001);
        assertEquals(0.6682, components.finalScore(), 0.0001);
    }

    @Test
    @DisplayName("협업 점수가 없으면 0으로 처리하고 fallback 지역 점수를 적용한다")
    void usesFallbackRegionScoreWhenCollaborativeSignalIsMissing() {
        UserPreferenceProfile profile = new UserPreferenceProfile(
                Set.of(1L),
                Map.of(1L, 0.9),
                Map.of("한식", 1.0),
                Map.of("서울", 1.0),
                List.of(new LikedRestaurantFeature(1L, 0.9, "서울", Set.of("한식"), LocalDateTime.now())),
                "서울"
        );
        RestaurantFeature candidate = new RestaurantFeature(
                102L,
                "식당 B",
                "주소",
                "부산",
                "b.png",
                List.of("한식"),
                0.6,
                3L
        );

        RecommendationScoreComponents components = scorer.score(profile, candidate, 0.0, 0.55);

        assertEquals(0.0, components.collaborativeScore(), 0.0001);
        assertEquals(0.55, components.regionScore(), 0.0001);
        assertEquals(1.0, components.userPreferenceScore(), 0.0001);
        assertEquals(1.0, components.categoryFitScore(), 0.0001);
        assertEquals(0.6, components.rankingAdjustmentScore(), 0.0001);
        assertEquals(0.825, components.finalScore(), 0.0001);
    }

    @Test
    @DisplayName("사용자 선호와 카테고리 적합도가 모두 0이면 감점하고 협업 점수를 절반만 반영한다")
    void penalizesZeroSimilarityCandidatesAndReducesCollaborativeInfluence() {
        UserPreferenceProfile profile = new UserPreferenceProfile(
                Set.of(1L),
                Map.of(1L, 0.9),
                Map.of("한식", 1.0),
                Map.of("서울", 1.0),
                List.of(new LikedRestaurantFeature(1L, 0.9, "서울", Set.of("한식"), LocalDateTime.now())),
                "서울"
        );
        RestaurantFeature candidate = new RestaurantFeature(
                103L,
                "식당 C",
                "주소",
                "서울",
                "c.png",
                List.of("카페"),
                0.8,
                7L
        );

        RecommendationScoreComponents components = scorer.score(profile, candidate, 0.8, 1.0);

        assertEquals(0.0, components.userPreferenceScore(), 0.0001);
        assertEquals(0.0, components.categoryFitScore(), 0.0001);
        assertEquals(0.8, components.rankingAdjustmentScore(), 0.0001);
        assertEquals(0.4, components.collaborativeScore(), 0.0001);
        assertEquals(1.0, components.regionScore(), 0.0001);
        assertEquals(0.03, components.finalScore(), 0.0001);
    }
}
