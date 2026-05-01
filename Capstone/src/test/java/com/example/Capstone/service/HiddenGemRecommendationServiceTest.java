package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Capstone.dto.response.HiddenGemRestaurantResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.HiddenGemRecommendationRepository;
import com.example.Capstone.repository.HiddenGemRestaurantRow;

@ExtendWith(MockitoExtension.class)
class HiddenGemRecommendationServiceTest {

    @Mock
    private HiddenGemRecommendationRepository hiddenGemRecommendationRepository;

    @InjectMocks
    private HiddenGemRecommendationService hiddenGemRecommendationService;

    @Test
    @DisplayName("지역명을 trim하고 숨은 맛집 점수 순서로 후보를 반환한다")
    void returnsHiddenGemRestaurantsOrderedByRecommendationScore() {
        when(hiddenGemRecommendationRepository.findHiddenGemCandidates("김량장동", 3))
                .thenReturn(List.of(
                        row(101L, "low-count-high-score", 2L, "96.0", "90.0"),
                        row(102L, "mid-count-good-score", 3L, "90.0", "88.0"),
                        row(103L, "one-count-outlier", 1L, "100.0", "100.0"),
                        row(104L, "popular-restaurant", 12L, "99.0", "99.0")
                ));

        HiddenGemRestaurantResponse response = hiddenGemRecommendationService.getHiddenGemRestaurants(" 김량장동 ");

        assertEquals("김량장동", response.regionTownName());
        assertEquals(10, response.limit());
        assertEquals(2, response.items().size());
        assertEquals(101L, response.items().get(0).restaurantId());
        assertEquals(new BigDecimal("91.50"), response.items().get(0).recommendationScore());
        assertEquals(new BigDecimal("90.00"), response.items().get(0).adjustedScore());
        assertEquals(2L, response.items().get(0).evaluationCount());
        assertEquals(102L, response.items().get(1).restaurantId());
        verify(hiddenGemRecommendationRepository).findHiddenGemCandidates("김량장동", 3);
    }

    @Test
    @DisplayName("결과는 최대 10개로 제한한다")
    void limitsResultToTenItems() {
        List<HiddenGemRestaurantRow> rows = new ArrayList<>();
        for (long index = 1; index <= 12; index++) {
            rows.add(row(index, "restaurant-" + index, 2L, "90.0", String.valueOf(100 - index)));
        }
        when(hiddenGemRecommendationRepository.findHiddenGemCandidates("삼가동", 3))
                .thenReturn(rows);

        HiddenGemRestaurantResponse response = hiddenGemRecommendationService.getHiddenGemRestaurants("삼가동");

        assertEquals(10, response.items().size());
        assertEquals(1, response.items().get(0).rank());
        assertEquals(10, response.items().get(9).rank());
    }

    @Test
    @DisplayName("후보가 없는 지역은 빈 결과를 반환한다")
    void returnsEmptyItemsWhenNoCandidatesExist() {
        when(hiddenGemRecommendationRepository.findHiddenGemCandidates("후보없음동", 3))
                .thenReturn(List.of());

        HiddenGemRestaurantResponse response = hiddenGemRecommendationService.getHiddenGemRestaurants("후보없음동");

        assertEquals("후보없음동", response.regionTownName());
        assertEquals(0, response.items().size());
    }

    @Test
    @DisplayName("regionTownName이 null이면 400 비즈니스 예외를 던진다")
    void throwsWhenRegionTownNameIsNull() {
        assertThrows(BusinessException.class, () -> hiddenGemRecommendationService.getHiddenGemRestaurants(null));

        verify(hiddenGemRecommendationRepository, never())
                .findHiddenGemCandidates(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt()
                );
    }

    @Test
    @DisplayName("regionTownName이 blank이면 400 비즈니스 예외를 던진다")
    void throwsWhenRegionTownNameIsBlank() {
        assertThrows(BusinessException.class, () -> hiddenGemRecommendationService.getHiddenGemRestaurants(" "));

        verify(hiddenGemRecommendationRepository, never())
                .findHiddenGemCandidates(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt()
                );
    }

    private HiddenGemRestaurantRow row(
            Long restaurantId,
            String restaurantName,
            Long evaluationCount,
            String averageAutoScore,
            String adjustedScore
    ) {
        return new HiddenGemRestaurantRow(
                restaurantId,
                restaurantName,
                "address-" + restaurantId,
                "용인시 처인구",
                "김량장동",
                new BigDecimal(averageAutoScore),
                evaluationCount,
                new BigDecimal(adjustedScore)
        );
    }
}
