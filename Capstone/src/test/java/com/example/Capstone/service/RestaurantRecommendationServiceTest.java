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
import com.example.Capstone.domain.RestaurantCategory;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.RestaurantRecommendationResponse;
import com.example.Capstone.recommendation.model.restaurant.RecommendationScoreComponents;
import com.example.Capstone.recommendation.model.restaurant.RestaurantFeature;
import com.example.Capstone.recommendation.scorer.RestaurantRecommendationScorer;
import com.example.Capstone.repository.RestaurantCategoryRepository;
import com.example.Capstone.repository.RestaurantRecommendationRepository;
import com.example.Capstone.repository.RestaurantRecommendationRepository.CandidateRestaurantRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.RankingSignalRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.UserInteractionRow;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RestaurantRecommendationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRecommendationRepository restaurantRecommendationRepository;

    @Mock
    private RestaurantCategoryRepository restaurantCategoryRepository;

    @Mock
    private RestaurantRecommendationScorer restaurantRecommendationScorer;

    @InjectMocks
    private RestaurantRecommendationService restaurantRecommendationService;

    @Test
    @DisplayName("동일 지역 후보가 4개 이상이면 fallback 없이 상위 4개를 반환한다")
    void returnsTopFourFromSameRegionWithoutFallback() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of(
                new UserInteractionRow(10L, "서울", new BigDecimal("90.0"), LocalDateTime.of(2026, 4, 12, 9, 0)),
                new UserInteractionRow(11L, "서울", new BigDecimal("80.0"), LocalDateTime.of(2026, 4, 12, 8, 0))
        ));
        when(restaurantRecommendationRepository.findSameRegionCandidates(userId, "서울", 200, 5)).thenReturn(List.of(
                candidate(101L, "서울"),
                candidate(102L, "서울"),
                candidate(103L, "서울"),
                candidate(104L, "서울"),
                candidate(105L, "서울")
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

        assertEquals("서울", response.baseRegionName());
        assertEquals(4, response.items().size());
        assertEquals(101L, response.items().get(0).restaurantId());
        assertEquals(104L, response.items().get(3).restaurantId());
        verify(restaurantRecommendationRepository, never())
                .findFallbackCandidates(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("동일 지역 후보가 4개 미만이면 fallback 후보를 추가해 4개를 채운다")
    void addsFallbackCandidatesWhenSameRegionIsInsufficient() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of(
                new UserInteractionRow(10L, "서울", new BigDecimal("90.0"), LocalDateTime.of(2026, 4, 12, 9, 0))
        ));
        when(restaurantRecommendationRepository.findSameRegionCandidates(userId, "서울", 200, 5)).thenReturn(List.of(
                candidate(101L, "서울"),
                candidate(102L, "서울")
        ));
        when(restaurantRecommendationRepository.findFallbackCandidates(userId, "서울", 100, 5)).thenReturn(List.of(
                candidate(201L, "부산"),
                candidate(202L, "대구")
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
        verify(restaurantRecommendationRepository).findFallbackCandidates(userId, "서울", 100, 5);
        verify(restaurantRecommendationScorer).score(any(), argThat(feature -> feature.restaurantId().equals(201L)), anyDouble(), eq(0.55));
    }

    @Test
    @DisplayName("사용자 상호작용 데이터가 없으면 빈 추천 결과를 반환한다")
    void returnsEmptyResponseWhenNoInteractionsExist() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(restaurantRecommendationRepository.findUserInteractions(userId)).thenReturn(List.of());

        RestaurantRecommendationResponse response = restaurantRecommendationService.getRestaurantRecommendations(userId);

        assertEquals(0, response.items().size());
        verify(restaurantRecommendationRepository, never()).findSameRegionCandidates(any(), any(), anyInt(), anyInt());
    }

    private void mockCategoryLookup() {
        List<RestaurantCategory> categories = List.of(
                category(10L, "한식"),
                category(11L, "카페"),
                category(101L, "한식"),
                category(102L, "한식"),
                category(103L, "한식"),
                category(104L, "한식"),
                category(105L, "한식"),
                category(201L, "한식"),
                category(202L, "한식")
        );

        when(restaurantCategoryRepository.findAllByRestaurantIdInOrderByRestaurantIdAscCategoryNameAsc(anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Long> ids = (List<Long>) invocation.getArgument(0);
                    List<RestaurantCategory> filtered = new ArrayList<>();
                    for (RestaurantCategory category : categories) {
                        if (ids.contains(category.getRestaurant().getId())) {
                            filtered.add(category);
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
        return new CandidateRestaurantRow(restaurantId, "식당-" + restaurantId, "주소", regionName, "image-" + restaurantId);
    }

    private RankingSignalRow ranking(Long restaurantId, String adjustedScore, Long evaluationCount) {
        return new RankingSignalRow(
                restaurantId,
                new BigDecimal(adjustedScore),
                new BigDecimal(adjustedScore),
                evaluationCount
        );
    }

    private RestaurantCategory category(Long restaurantId, String categoryName) {
        Restaurant restaurant = Restaurant.builder()
                .name("식당")
                .address("주소")
                .regionName("서울")
                .imageUrl("image")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .build();
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);

        RestaurantCategory category = RestaurantCategory.builder()
                .restaurant(restaurant)
                .categoryName(categoryName)
                .build();
        ReflectionTestUtils.setField(category, "id", restaurantId * 100);
        return category;
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
