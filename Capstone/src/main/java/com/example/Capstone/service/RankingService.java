package com.example.Capstone.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.RestaurantRankingItemResponse;
import com.example.Capstone.dto.response.RestaurantRankingResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRankingRow;
import com.example.Capstone.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int SMOOTHING_CONSTANT = 5;

    private final RestaurantRepository restaurantRepository;

    public RestaurantRankingResponse getRestaurantRankings(String regionName, String category, Integer limit) {
        String normalizedRegionName = normalize(regionName);
        String normalizedCategory = normalize(category);
        int normalizedLimit = normalizeLimit(limit);
        String scope = normalizedRegionName == null ? "NATIONAL" : "REGION";

        List<RestaurantRankingRow> rankingRows = restaurantRepository.findRestaurantRankings(
                normalizedRegionName,
                normalizedCategory,
                normalizedLimit,
                SMOOTHING_CONSTANT
        );

        Map<Long, List<String>> categoryMap = loadCategoryMap(rankingRows);
        List<RestaurantRankingItemResponse> items = toItems(rankingRows, categoryMap);

        return RestaurantRankingResponse.of(
                LocalDateTime.now(),
                scope,
                normalizedRegionName,
                normalizedCategory,
                normalizedLimit,
                items
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < 1) {
            throw new BusinessException("limit는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private Map<Long, List<String>> loadCategoryMap(List<RestaurantRankingRow> rankingRows) {
        if (rankingRows.isEmpty()) {
            return Map.of();
        }

        List<Long> restaurantIds = rankingRows.stream()
                .map(RestaurantRankingRow::restaurantId)
                .toList();

        Map<Long, List<String>> categoryMap = new HashMap<>();
        for (Restaurant restaurant : restaurantRepository.findAllById(restaurantIds)) {
            categoryMap.put(restaurant.getId(), restaurant.getCategoryNames());
        }
        return categoryMap;
    }

    private List<RestaurantRankingItemResponse> toItems(
            List<RestaurantRankingRow> rankingRows,
            Map<Long, List<String>> categoryMap
    ) {
        return java.util.stream.IntStream.range(0, rankingRows.size())
                .mapToObj(index -> RestaurantRankingItemResponse.of(
                        index + 1,
                        rankingRows.get(index),
                        categoryMap.getOrDefault(rankingRows.get(index).restaurantId(), List.of())
                ))
                .toList();
    }
}
