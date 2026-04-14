package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.RestaurantRecommendationResponse;
import com.example.Capstone.recommendation.model.restaurant.RecommendationScoreComponents;
import com.example.Capstone.recommendation.model.restaurant.RestaurantFeature;
import com.example.Capstone.recommendation.scorer.RestaurantRecommendationScorer;
import com.example.Capstone.repository.RestaurantRecommendationRepository;
import com.example.Capstone.repository.RestaurantRecommendationRepository.CandidateRestaurantRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.RankingSignalRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.UserInteractionRow;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RestaurantRecommendationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantRecommendationRepository restaurantRecommendationRepository;

    @Mock
    private RestaurantRecommendationScorer restaurantRecommendationScorer;

    @InjectMocks
    private RestaurantRecommendationService restaurantRecommendationService;

    @Test
    @DisplayName("returns same-region top four without fallback")
    void returnsTopFourFromSameRegionWithoutFallback() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of(
                new UserInteractionRow(10L, "region-a", new BigDecimal("90.0"), LocalDateTime.of(2026, 4, 12, 9, 0)),
                new UserInteractionRow(11L, "region-a", new BigDecimal("80.0"), LocalDateTime.of(2026, 4, 12, 8, 0))
        ));
        when(restaurantRecommendationRepository.findSameRegionCandidates(userId, "region-a", 200, 5)).thenReturn(List.of(
                candidate(101L, "region-a"),
                candidate(102L, "region-a"),
                candidate(103L, "region-a"),
                candidate(104L, "region-a"),
                candidate(105L, "region-a")
        ));
        when(restaurantRecommendationRepository.findRankingSignals(anyList(), eq(5))).thenReturn(List.of(
                ranking(101L, "90.0", 20L),
                ranking(102L, "80.0", 18L),
                ranking(103L, "70.0", 16L),
                ranking(104L, "60.0", 14L),
                ranking(105L, "50.0", 12L)
        ));
        when(restaurantRecommendationRepository.findNeighborInteractions(userId, 2, 50)).thenReturn(List.of());
        mockCategoryLookup();
        mockScores(Map.of(
                101L, 0.91,
                102L, 0.81,
                103L, 0.71,
                104L, 0.61,
                105L, 0.51
        ));

        RestaurantRecommendationResponse response = restaurantRecommendationService.getRestaurantRecommendations(userId);

        assertEquals("region-a", response.baseRegionName());
        assertEquals(4, response.items().size());
        assertEquals(101L, response.items().get(0).restaurantId());
        assertEquals(104L, response.items().get(3).restaurantId());
        verify(restaurantRecommendationRepository, never())
                .findFallbackCandidates(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("adds fallback candidates when same-region candidates are insufficient")
    void addsFallbackCandidatesWhenSameRegionIsInsufficient() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of(
                new UserInteractionRow(10L, "region-a", new BigDecimal("90.0"), LocalDateTime.of(2026, 4, 12, 9, 0))
        ));
        when(restaurantRecommendationRepository.findSameRegionCandidates(userId, "region-a", 200, 5)).thenReturn(List.of(
                candidate(101L, "region-a"),
                candidate(102L, "region-a")
        ));
        when(restaurantRecommendationRepository.findFallbackCandidates(userId, "region-a", 100, 5)).thenReturn(List.of(
                candidate(201L, "region-b"),
                candidate(202L, "region-c")
        ));
        when(restaurantRecommendationRepository.findRankingSignals(anyList(), eq(5))).thenReturn(List.of(
                ranking(101L, "90.0", 20L),
                ranking(102L, "80.0", 18L),
                ranking(201L, "85.0", 15L),
                ranking(202L, "75.0", 10L)
        ));
        when(restaurantRecommendationRepository.findNeighborInteractions(userId, 2, 50)).thenReturn(List.of());
        mockCategoryLookup();
        mockScores(Map.of(
                101L, 0.95,
                102L, 0.85,
                201L, 0.80,
                202L, 0.70
        ));

        RestaurantRecommendationResponse response = restaurantRecommendationService.getRestaurantRecommendations(userId);

        assertEquals(4, response.items().size());
        assertFalse(response.items().get(0).fallbackRegion());
        assertTrue(response.items().get(2).fallbackRegion());
        verify(restaurantRecommendationRepository).findFallbackCandidates(userId, "region-a", 100, 5);
        verify(restaurantRecommendationScorer).score(
                any(),
                argThat(feature -> feature.restaurantId().equals(201L)),
                anyDouble(),
                eq(0.55)
        );
    }

    @Test
    @DisplayName("returns empty response when no interactions exist")
    void returnsEmptyResponseWhenNoInteractionsExist() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of());

        RestaurantRecommendationResponse response = restaurantRecommendationService.getRestaurantRecommendations(userId);

        assertEquals(0, response.items().size());
        verify(restaurantRecommendationRepository, never()).findSameRegionCandidates(any(), any(), anyInt(), anyInt());
    }

    private void mockCategoryLookup() {
        List<Restaurant> restaurants = List.of(
                restaurant(10L, "category-a"),
                restaurant(11L, "category-b"),
                restaurant(101L, "category-a"),
                restaurant(102L, "category-a"),
                restaurant(103L, "category-a"),
                restaurant(104L, "category-a"),
                restaurant(105L, "category-a"),
                restaurant(201L, "category-a"),
                restaurant(202L, "category-a")
        );

        when(restaurantRepository.findAllById(anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Long> ids = (List<Long>) invocation.getArgument(0);
                    List<Restaurant> filtered = new ArrayList<>();
                    for (Restaurant restaurant : restaurants) {
                        if (ids.contains(restaurant.getId())) {
                            filtered.add(restaurant);
                        }
                    }
                    return filtered;
                });
    }

    private void mockScores(Map<Long, Double> scoreMap) {
        when(restaurantRecommendationScorer.score(any(), any(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    RestaurantFeature feature = invocation.getArgument(1);
                    double finalScore = scoreMap.getOrDefault(feature.restaurantId(), 0.0);
                    return new RecommendationScoreComponents(
                            finalScore,
                            finalScore,
                            finalScore,
                            finalScore,
                            0.0,
                            1.0
                    );
                });
    }

    private CandidateRestaurantRow candidate(Long restaurantId, String regionName) {
        return new CandidateRestaurantRow(restaurantId, "restaurant-" + restaurantId, "address", regionName, "image-" + restaurantId);
    }

    private RankingSignalRow ranking(Long restaurantId, String adjustedScore, Long evaluationCount) {
        return new RankingSignalRow(
                restaurantId,
                new BigDecimal(adjustedScore),
                new BigDecimal(adjustedScore),
                evaluationCount
        );
    }

    private Restaurant restaurant(Long restaurantId, String categoryName) {
        Restaurant restaurant = Restaurant.builder()
                .name("restaurant")
                .address("address")
                .categoryName(categoryName)
                .regionName("region-a")
                .imageUrl("image")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .build();
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        return restaurant;
    }

    private User user(Long userId) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId("provider-" + userId)
                .nickname("user-" + userId)
                .profileImageUrl("image")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
