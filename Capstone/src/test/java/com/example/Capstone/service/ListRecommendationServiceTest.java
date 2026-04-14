package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.example.Capstone.dto.response.ListRecommendationResponse;
import com.example.Capstone.recommendation.model.list.ListRecommendationFeature;
import com.example.Capstone.recommendation.model.list.ListRecommendationScoreComponents;
import com.example.Capstone.recommendation.scorer.ListRecommendationScorer;
import com.example.Capstone.repository.ListRecommendationRepository;
import com.example.Capstone.repository.ListRecommendationRepository.CandidateListRestaurantRow;
import com.example.Capstone.repository.ListRecommendationRepository.CandidateListSummaryRow;
import com.example.Capstone.repository.ListRecommendationRepository.UserListInteractionRow;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ListRecommendationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ListRecommendationRepository listRecommendationRepository;

    @Mock
    private ListRecommendationScorer listRecommendationScorer;

    @InjectMocks
    private ListRecommendationService listRecommendationService;

    @Test
    @DisplayName("returns top twenty same-region lists without fallback")
    void returnsTopTwentyFromSameRegionWithoutFallback() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(listRecommendationRepository.findUserListInteractions(userId)).thenReturn(List.of(
                interaction(1L, 10L, "region-a", "9.0", "8.0", "7.0", "84.0", LocalDateTime.of(2026, 4, 12, 10, 0))
        ));

        List<CandidateListSummaryRow> sameRegionSummaries = new ArrayList<>();
        for (long index = 0; index < 21; index++) {
            sameRegionSummaries.add(summary(101L + index, 201L + index, "region-a"));
        }

        when(listRecommendationRepository.findSameRegionCandidateLists(userId, "region-a", 200, 5, 5))
                .thenReturn(sameRegionSummaries);
        mockCandidateRestaurants(sameRegionSummaries, List.of());
        mockCategoryLookup(sameRegionSummaries, List.of());
        when(listRecommendationRepository.findCandidateOwnerInteractions(eq(userId), anyList(), eq(2)))
                .thenReturn(List.of());
        mockScores(scoreMap(sameRegionSummaries, 1.00, 0.01));

        ListRecommendationResponse response = listRecommendationService.getListRecommendations(userId);

        assertEquals("region-a", response.baseRegionName());
        assertEquals(20, response.items().size());
        assertEquals(101L, response.items().get(0).listId());
        assertEquals(120L, response.items().get(19).listId());
        assertFalse(response.items().get(0).fallbackRegion());
        verify(listRecommendationRepository, never())
                .findFallbackCandidateLists(any(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("fills remaining slots with fallback lists")
    void fillsRemainingSlotsWithFallbackCandidates() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(listRecommendationRepository.findUserListInteractions(userId)).thenReturn(List.of(
                interaction(1L, 10L, "region-a", "9.0", "8.0", "7.0", "84.0", LocalDateTime.of(2026, 4, 12, 10, 0))
        ));

        List<CandidateListSummaryRow> sameRegionSummaries = List.of(
                summary(101L, 201L, "region-a"),
                summary(102L, 202L, "region-a"),
                summary(103L, 203L, "region-a")
        );
        List<CandidateListSummaryRow> fallbackSummaries = new ArrayList<>();
        for (long index = 0; index < 20; index++) {
            fallbackSummaries.add(summary(301L + index, 401L + index, "region-b"));
        }

        when(listRecommendationRepository.findSameRegionCandidateLists(userId, "region-a", 200, 5, 5))
                .thenReturn(sameRegionSummaries);
        when(listRecommendationRepository.findFallbackCandidateLists(userId, "region-a", 120, 5, 5))
                .thenReturn(fallbackSummaries);
        mockCandidateRestaurants(sameRegionSummaries, fallbackSummaries);
        mockCategoryLookup(sameRegionSummaries, fallbackSummaries);
        when(listRecommendationRepository.findCandidateOwnerInteractions(eq(userId), anyList(), eq(2)))
                .thenReturn(List.of());

        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put(101L, 0.30);
        scoreMap.put(102L, 0.20);
        scoreMap.put(103L, 0.10);
        scoreMap.putAll(scoreMap(fallbackSummaries, 0.99, 0.01));
        mockScores(scoreMap);

        ListRecommendationResponse response = listRecommendationService.getListRecommendations(userId);

        assertEquals(20, response.items().size());
        assertEquals(101L, response.items().get(0).listId());
        assertEquals(102L, response.items().get(1).listId());
        assertEquals(103L, response.items().get(2).listId());
        assertFalse(response.items().get(0).fallbackRegion());
        assertTrue(response.items().get(3).fallbackRegion());
        verify(listRecommendationRepository).findFallbackCandidateLists(userId, "region-a", 120, 5, 5);
    }

    @Test
    @DisplayName("returns empty response when user profile is empty")
    void returnsEmptyResponseWhenUserProfileIsEmpty() {
        Long userId = 1L;
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user(userId)));
        when(listRecommendationRepository.findUserListInteractions(userId)).thenReturn(List.of());

        ListRecommendationResponse response = listRecommendationService.getListRecommendations(userId);

        assertEquals(0, response.items().size());
        verify(listRecommendationRepository, never())
                .findSameRegionCandidateLists(any(), any(), anyInt(), anyInt(), anyInt());
    }

    private void mockCandidateRestaurants(
            List<CandidateListSummaryRow> sameRegionSummaries,
            List<CandidateListSummaryRow> fallbackSummaries
    ) {
        List<CandidateListRestaurantRow> allRows = new ArrayList<>();
        appendRestaurants(allRows, sameRegionSummaries, 1000L);
        appendRestaurants(allRows, fallbackSummaries, 2000L);

        when(listRecommendationRepository.findCandidateListRestaurants(anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Long> listIds = (List<Long>) invocation.getArgument(0);
                    return allRows.stream()
                            .filter(row -> listIds.contains(row.listId()))
                            .toList();
                });
    }

    private void mockCategoryLookup(
            List<CandidateListSummaryRow> sameRegionSummaries,
            List<CandidateListSummaryRow> fallbackSummaries
    ) {
        List<Restaurant> restaurants = new ArrayList<>();
        restaurants.add(restaurant(10L, "category-a"));

        for (long restaurantId = 1000L; restaurantId < 1000L + sameRegionSummaries.size() * 5; restaurantId++) {
            restaurants.add(restaurant(restaurantId, "category-a"));
        }
        for (long restaurantId = 2000L; restaurantId < 2000L + fallbackSummaries.size() * 5; restaurantId++) {
            restaurants.add(restaurant(restaurantId, "category-a"));
        }

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
        when(listRecommendationScorer.score(any(), any(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    ListRecommendationFeature feature = invocation.getArgument(1);
                    double finalScore = scoreMap.getOrDefault(feature.listId(), 0.0);
                    return new ListRecommendationScoreComponents(
                            finalScore,
                            finalScore,
                            finalScore,
                            finalScore,
                            finalScore,
                            finalScore,
                            finalScore,
                            0.5,
                            0.0,
                            invocation.getArgument(3)
                    );
                });
    }

    private void appendRestaurants(
            List<CandidateListRestaurantRow> rows,
            List<CandidateListSummaryRow> summaries,
            long startRestaurantId
    ) {
        long restaurantId = startRestaurantId;
        for (CandidateListSummaryRow summary : summaries) {
            for (int count = 0; count < 5; count++) {
                rows.add(new CandidateListRestaurantRow(
                        summary.listId(),
                        restaurantId++,
                        "restaurant-" + restaurantId,
                        new BigDecimal("9.0"),
                        new BigDecimal("8.0"),
                        new BigDecimal("7.0"),
                        new BigDecimal("84.0")
                ));
            }
        }
    }

    private Map<Long, Double> scoreMap(List<CandidateListSummaryRow> summaries, double start, double step) {
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        double score = start;
        for (CandidateListSummaryRow summary : summaries) {
            scoreMap.put(summary.listId(), score);
            score -= step;
        }
        return scoreMap;
    }

    private UserListInteractionRow interaction(
            Long listId,
            Long restaurantId,
            String regionName,
            String taste,
            String value,
            String mood,
            String autoScore,
            LocalDateTime updatedAt
    ) {
        return new UserListInteractionRow(
                listId,
                restaurantId,
                regionName,
                new BigDecimal(taste),
                new BigDecimal(value),
                new BigDecimal(mood),
                new BigDecimal(autoScore),
                updatedAt
        );
    }

    private CandidateListSummaryRow summary(Long listId, Long ownerId, String regionName) {
        return new CandidateListSummaryRow(
                listId,
                "list-" + listId,
                "description",
                regionName,
                ownerId,
                "owner-" + ownerId,
                "profile-" + ownerId,
                5L,
                new BigDecimal("80.0"),
                new BigDecimal("82.0"),
                LocalDateTime.of(2026, 4, 12, 12, 0)
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
