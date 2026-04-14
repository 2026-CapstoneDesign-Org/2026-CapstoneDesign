package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.RestaurantRankingResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRankingRow;
import com.example.Capstone.repository.RestaurantRepository;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    @DisplayName("uses default limit when request limit is null")
    void usesDefaultLimitWhenLimitIsNull() {
        when(restaurantRepository.findRestaurantRankings(null, null, 20, 5))
                .thenReturn(List.of());

        RestaurantRankingResponse response = rankingService.getRestaurantRankings(null, null, null);

        assertEquals(20, response.limit());
        assertEquals("NATIONAL", response.scope());
        verify(restaurantRepository).findRestaurantRankings(null, null, 20, 5);
        verify(restaurantRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("clamps limit to maximum")
    void clampsLimitToMaximum() {
        when(restaurantRepository.findRestaurantRankings("region-a", "category-a", 50, 5))
                .thenReturn(List.of());

        RestaurantRankingResponse response = rankingService.getRestaurantRankings("region-a", "category-a", 99);

        assertEquals(50, response.limit());
        assertEquals("REGION", response.scope());
        verify(restaurantRepository).findRestaurantRankings("region-a", "category-a", 50, 5);
    }

    @Test
    @DisplayName("throws when limit is below one")
    void throwsWhenLimitIsLessThanOne() {
        assertThrows(BusinessException.class, () -> rankingService.getRestaurantRankings(null, null, 0));

        verify(restaurantRepository, never())
                .findRestaurantRankings(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()
                );
    }

    @Test
    @DisplayName("maps categories from restaurant rows and assigns ranks")
    void mapsCategoriesAndAssignsRanks() {
        RestaurantRankingRow first = new RestaurantRankingRow(
                10L,
                "Restaurant A",
                "region-a",
                "a.png",
                new BigDecimal("91.5"),
                2L,
                new BigDecimal("87.428571")
        );
        RestaurantRankingRow second = new RestaurantRankingRow(
                11L,
                "Restaurant B",
                "region-a",
                "b.png",
                new BigDecimal("88.0"),
                1L,
                new BigDecimal("84.000000")
        );

        when(restaurantRepository.findRestaurantRankings("region-a", "category-a", 20, 5))
                .thenReturn(List.of(first, second));
        when(restaurantRepository.findAllById(eq(List.of(10L, 11L))))
                .thenReturn(List.of(
                        restaurant(10L, "category-a"),
                        restaurant(11L, "category-b")
                ));

        RestaurantRankingResponse response = rankingService.getRestaurantRankings("region-a", "category-a", 20);

        assertEquals(2, response.items().size());
        assertEquals(1, response.items().get(0).rank());
        assertEquals(10L, response.items().get(0).restaurantId());
        assertEquals(List.of("category-a"), response.items().get(0).categories());
        assertEquals(new BigDecimal("87.43"), response.items().get(0).adjustedScore());
        assertEquals(2, response.items().get(0).evaluationCount());

        assertEquals(2, response.items().get(1).rank());
        assertEquals(List.of("category-b"), response.items().get(1).categories());
    }

    private Restaurant restaurant(Long restaurantId, String categoryName) {
        Restaurant restaurant = Restaurant.builder()
                .name("Restaurant")
                .address("Address")
                .categoryName(categoryName)
                .regionName("region-a")
                .imageUrl("image")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .build();
        ReflectionTestUtils.setField(restaurant, "id", restaurantId);
        return restaurant;
    }
}
