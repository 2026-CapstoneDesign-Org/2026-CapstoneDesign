package com.example.Capstone.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.dto.response.HiddenGemRestaurantItemResponse;
import com.example.Capstone.dto.response.HiddenGemRestaurantResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.HiddenGemRecommendationRepository;
import com.example.Capstone.repository.HiddenGemRestaurantRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HiddenGemRecommendationService {

    private static final int RESULT_LIMIT = 10;
    private static final int SMOOTHING_CONSTANT = 3;
    private static final long MIN_EVALUATION_COUNT = 2;
    private static final int MIN_POPULARITY_CUTOFF = 3;
    private static final int MAX_POPULARITY_CUTOFF = 10;
    private static final double QUALITY_WEIGHT = 0.85;
    private static final double HIDDENNESS_WEIGHT = 0.15;

    private final HiddenGemRecommendationRepository hiddenGemRecommendationRepository;

    public HiddenGemRestaurantResponse getHiddenGemRestaurants(String regionTownName) {
        String normalizedRegionTownName = normalizeRegionTownName(regionTownName);
        List<HiddenGemRestaurantRow> rows = hiddenGemRecommendationRepository.findHiddenGemCandidates(
                normalizedRegionTownName,
                SMOOTHING_CONSTANT
        );
        int popularityCutoff = calculatePopularityCutoff(rows);

        List<ScoredHiddenGem> scoredItems = rows.stream()
                .filter(row -> evaluationCount(row) >= MIN_EVALUATION_COUNT)
                .filter(row -> evaluationCount(row) <= popularityCutoff)
                .map(row -> new ScoredHiddenGem(
                        row,
                        calculateRecommendationScore(row, popularityCutoff)
                ))
                .sorted(hiddenGemComparator())
                .limit(RESULT_LIMIT)
                .toList();

        List<HiddenGemRestaurantItemResponse> items = IntStream.range(0, scoredItems.size())
                .mapToObj(index -> scoredItems.get(index).toResponse(index + 1))
                .toList();

        return HiddenGemRestaurantResponse.of(
                LocalDateTime.now(),
                normalizedRegionTownName,
                RESULT_LIMIT,
                items
        );
    }

    private String normalizeRegionTownName(String regionTownName) {
        if (regionTownName == null || regionTownName.isBlank()) {
            throw new BusinessException("regionTownName은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        return regionTownName.trim();
    }

    private int calculatePopularityCutoff(List<HiddenGemRestaurantRow> rows) {
        if (rows.isEmpty()) {
            return MIN_POPULARITY_CUTOFF;
        }

        double averageEvaluationCount = rows.stream()
                .mapToLong(this::evaluationCount)
                .average()
                .orElse(MIN_POPULARITY_CUTOFF);

        int averageBasedCutoff = (int) Math.ceil(averageEvaluationCount);
        return Math.min(MAX_POPULARITY_CUTOFF, Math.max(MIN_POPULARITY_CUTOFF, averageBasedCutoff));
    }

    private double calculateRecommendationScore(HiddenGemRestaurantRow row, int popularityCutoff) {
        double hiddennessScore = calculateHiddennessScore(evaluationCount(row), popularityCutoff);
        return ((toDouble(row.adjustedScore()) / 100.0) * QUALITY_WEIGHT + hiddennessScore * HIDDENNESS_WEIGHT) * 100.0;
    }

    private double calculateHiddennessScore(long evaluationCount, int popularityCutoff) {
        double denominator = Math.max(1.0, popularityCutoff - MIN_EVALUATION_COUNT);
        double hiddennessScore = 1.0 - ((evaluationCount - MIN_EVALUATION_COUNT) / denominator);
        return Math.max(0.0, Math.min(1.0, hiddennessScore));
    }

    private Comparator<ScoredHiddenGem> hiddenGemComparator() {
        return Comparator
                .comparingDouble(ScoredHiddenGem::recommendationScore)
                .reversed()
                .thenComparing(item -> toDouble(item.row().adjustedScore()), Comparator.reverseOrder())
                .thenComparingLong(item -> evaluationCount(item.row()))
                .thenComparing(item -> toDouble(item.row().averageAutoScore()), Comparator.reverseOrder())
                .thenComparing(item -> item.row().restaurantId());
    }

    private long evaluationCount(HiddenGemRestaurantRow row) {
        return row.evaluationCount() == null ? 0L : row.evaluationCount();
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private record ScoredHiddenGem(
            HiddenGemRestaurantRow row,
            double recommendationScore
    ) {
        private HiddenGemRestaurantItemResponse toResponse(int rank) {
            return new HiddenGemRestaurantItemResponse(
                    rank,
                    row.restaurantId(),
                    row.restaurantName(),
                    row.address(),
                    row.regionName(),
                    row.regionTownName(),
                    scale(recommendationScore),
                    scale(row.adjustedScore()),
                    scale(row.averageAutoScore()),
                    row.evaluationCount() == null ? 0L : row.evaluationCount()
            );
        }

        private BigDecimal scale(double value) {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        }

        private BigDecimal scale(BigDecimal value) {
            return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
