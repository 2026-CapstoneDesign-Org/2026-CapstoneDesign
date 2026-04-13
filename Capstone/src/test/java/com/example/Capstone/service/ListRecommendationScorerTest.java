package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.recommendation.model.list.ListRecommendationFeature;
import com.example.Capstone.recommendation.model.list.ListRecommendationScoreComponents;
import com.example.Capstone.recommendation.model.list.ListRecommendationUserProfile;
import com.example.Capstone.recommendation.model.list.OwnerFeature;
import com.example.Capstone.recommendation.model.list.ScoreVector;
import com.example.Capstone.recommendation.scorer.ListRecommendationScorer;

class ListRecommendationScorerTest {

    private final ListRecommendationScorer scorer = new ListRecommendationScorer();

    @Test
    @DisplayName("리스트 추천 점수를 고정 가중치대로 계산한다")
    void calculatesScoreUsingConfiguredWeights() {
        ListRecommendationUserProfile profile = new ListRecommendationUserProfile(
                Set.of(1L, 2L),
                Map.of(1L, 0.9, 2L, 0.7),
                Map.of("한식", 0.6, "카페", 0.4),
                Map.of("서울", 1.0),
                new ScoreVector(0.8, 0.6, 0.4),
                "서울"
        );
        ListRecommendationFeature candidate = new ListRecommendationFeature(
                101L,
                "서울 리스트",
                "설명",
                "서울",
                new OwnerFeature(11L, "owner", "profile"),
                8,
                Map.of(1L, 0.7, 3L, 0.3),
                Map.of("한식", 0.75, "분식", 0.25),
                new ScoreVector(0.8, 0.6, 0.4),
                0.8,
                0.8,
                List.of("한식", "분식")
        );

        ListRecommendationScoreComponents components = scorer.score(profile, candidate, 0.4, 1.0);

        assertEquals(0.6300, components.restaurantMatchScore(), 0.0001);
        assertEquals(0.4500, components.categoryMatchScore(), 0.0001);
        assertEquals(1.0000, components.scoreStyleMatchScore(), 0.0001);
        assertEquals(0.6410, components.preferenceMatchScore(), 0.0001);
        assertEquals(0.8000, components.qualityScore(), 0.0001);
        assertEquals(0.4000, components.collaborativeScore(), 0.0001);
        assertEquals(1.0000, components.regionScore(), 0.0001);
        assertEquals(0.7026, components.finalScore(), 0.0001);
    }

    @Test
    @DisplayName("fallback 지역 점수와 품질 점수를 함께 반영한다")
    void appliesFallbackRegionScore() {
        ListRecommendationUserProfile profile = new ListRecommendationUserProfile(
                Set.of(1L),
                Map.of(1L, 0.9),
                Map.of("한식", 0.6, "카페", 0.4),
                Map.of("서울", 1.0),
                new ScoreVector(0.8, 0.6, 0.4),
                "서울"
        );
        ListRecommendationFeature candidate = new ListRecommendationFeature(
                102L,
                "부산 리스트",
                "설명",
                "부산",
                new OwnerFeature(12L, "owner", "profile"),
                5,
                Map.of(1L, 1.0),
                Map.of("한식", 1.0),
                new ScoreVector(0.8, 0.6, 0.4),
                0.6,
                0.6,
                List.of("한식")
        );

        ListRecommendationScoreComponents components = scorer.score(profile, candidate, 0.0, 0.65);

        assertEquals(0.8150, components.preferenceMatchScore(), 0.0001);
        assertEquals(0.5750, components.qualityScore(), 0.0001);
        assertEquals(0.6500, components.regionScore(), 0.0001);
        assertEquals(0.0000, components.collaborativeScore(), 0.0001);
        assertEquals(0.6608, components.finalScore(), 0.0001);
    }

    @Test
    @DisplayName("0 유사도 후보는 협업 점수를 절반만 반영하고 감점한다")
    void penalizesZeroSimilarityCandidate() {
        ListRecommendationUserProfile profile = new ListRecommendationUserProfile(
                Set.of(1L),
                Map.of(1L, 0.9),
                Map.of("한식", 1.0),
                Map.of("서울", 1.0),
                new ScoreVector(0.8, 0.6, 0.4),
                "서울"
        );
        ListRecommendationFeature candidate = new ListRecommendationFeature(
                103L,
                "비유사 리스트",
                "설명",
                "서울",
                new OwnerFeature(13L, "owner", "profile"),
                5,
                Map.of(3L, 1.0),
                Map.of("양식", 1.0),
                ScoreVector.zero(),
                0.8,
                0.8,
                List.of("양식")
        );

        ListRecommendationScoreComponents components = scorer.score(profile, candidate, 0.8, 1.0);

        assertEquals(0.0000, components.restaurantMatchScore(), 0.0001);
        assertEquals(0.0000, components.categoryMatchScore(), 0.0001);
        assertEquals(0.0000, components.preferenceMatchScore(), 0.0001);
        assertEquals(0.4000, components.collaborativeScore(), 0.0001);
        assertEquals(0.2350, components.finalScore(), 0.0001);
    }
}
