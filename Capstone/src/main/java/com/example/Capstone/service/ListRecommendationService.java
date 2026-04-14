package com.example.Capstone.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.response.ListRecommendationItemResponse;
import com.example.Capstone.dto.response.ListRecommendationResponse;
import com.example.Capstone.dto.response.ListRecommendationScoreDetailResponse;
import com.example.Capstone.dto.response.RecommendationOwnerResponse;
import com.example.Capstone.recommendation.model.list.ListRecommendationFeature;
import com.example.Capstone.recommendation.model.list.ListRecommendationScoreComponents;
import com.example.Capstone.recommendation.model.list.ListRecommendationUserProfile;
import com.example.Capstone.recommendation.model.list.OwnerFeature;
import com.example.Capstone.recommendation.model.list.ScoreVector;
import com.example.Capstone.recommendation.scorer.ListRecommendationScorer;
import com.example.Capstone.repository.ListRecommendationRepository;
import com.example.Capstone.repository.ListRecommendationRepository.CandidateListRestaurantRow;
import com.example.Capstone.repository.ListRecommendationRepository.CandidateListSummaryRow;
import com.example.Capstone.repository.ListRecommendationRepository.OwnerInteractionRow;
import com.example.Capstone.repository.ListRecommendationRepository.UserListInteractionRow;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListRecommendationService {

    private static final int RESULT_LIMIT = 20;
    private static final int SAME_REGION_CANDIDATE_LIMIT = 200;
    private static final int FALLBACK_CANDIDATE_LIMIT = 120;
    private static final int MIN_COMMON_RESTAURANTS = 2;
    private static final int MIN_RESTAURANT_COUNT = 5;
    private static final int QUALITY_SMOOTHING_CONSTANT = 5;

    private static final double SAME_REGION_SCORE = 1.0;
    private static final double FALLBACK_REGION_SCORE = 0.65;

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final ListRecommendationRepository listRecommendationRepository;
    private final ListRecommendationScorer listRecommendationScorer;

    public ListRecommendationResponse getListRecommendations(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        ListRecommendationUserProfile profile = buildUserProfile(userId);
        if (profile.dominantRegion() == null) {
            return ListRecommendationResponse.of(LocalDateTime.now(), null, RESULT_LIMIT, List.of());
        }

        List<ListRecommendationFeature> sameRegionFeatures = loadCandidateFeatures(
                listRecommendationRepository.findSameRegionCandidateLists(
                        userId,
                        profile.dominantRegion(),
                        SAME_REGION_CANDIDATE_LIMIT,
                        MIN_RESTAURANT_COUNT,
                        QUALITY_SMOOTHING_CONSTANT
                )
        );

        List<ListRecommendationFeature> fallbackFeatures = List.of();
        if (sameRegionFeatures.size() < RESULT_LIMIT) {
            fallbackFeatures = loadCandidateFeatures(
                    listRecommendationRepository.findFallbackCandidateLists(
                            userId,
                            profile.dominantRegion(),
                            FALLBACK_CANDIDATE_LIMIT,
                            MIN_RESTAURANT_COUNT,
                            QUALITY_SMOOTHING_CONSTANT
                    )
            );
        }

        Map<Long, Double> collaborativeScoreMap = loadCollaborativeScoreMap(
                userId,
                profile,
                mergeOwnerIds(sameRegionFeatures, fallbackFeatures)
        );

        List<ScoredListRecommendation> scoredSameRegion = scoreCandidates(
                profile,
                sameRegionFeatures,
                collaborativeScoreMap,
                false,
                SAME_REGION_SCORE
        );
        List<ScoredListRecommendation> scoredFallback = scoreCandidates(
                profile,
                fallbackFeatures,
                collaborativeScoreMap,
                true,
                FALLBACK_REGION_SCORE
        );

        List<ListRecommendationItemResponse> items = new ArrayList<>();
        items.addAll(scoredSameRegion.stream()
                .sorted(recommendationComparator())
                .limit(RESULT_LIMIT)
                .map(ScoredListRecommendation::toResponse)
                .toList());

        if (items.size() < RESULT_LIMIT) {
            items.addAll(scoredFallback.stream()
                    .sorted(recommendationComparator())
                    .limit(RESULT_LIMIT - items.size())
                    .map(ScoredListRecommendation::toResponse)
                    .toList());
        }

        return ListRecommendationResponse.of(
                LocalDateTime.now(),
                profile.dominantRegion(),
                RESULT_LIMIT,
                withRank(items)
        );
    }

    private ListRecommendationUserProfile buildUserProfile(Long userId) {
        List<UserListInteractionRow> interactions = listRecommendationRepository.findUserListInteractions(userId);
        if (interactions.isEmpty()) {
            return ListRecommendationUserProfile.empty();
        }

        List<Long> restaurantIds = interactions.stream()
                .map(UserListInteractionRow::restaurantId)
                .distinct()
                .toList();
        Map<Long, List<String>> categoryMap = loadCategoryMap(restaurantIds);

        Map<Long, Double> restaurantBestScore = new LinkedHashMap<>();
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        Map<String, Double> regionTotals = new LinkedHashMap<>();
        Set<Long> ownedRestaurantIds = new LinkedHashSet<>();

        double tasteWeightedSum = 0.0;
        double valueWeightedSum = 0.0;
        double moodWeightedSum = 0.0;
        double totalWeight = 0.0;

        for (UserListInteractionRow interaction : interactions) {
            double normalizedScore = normalizeScore(interaction.autoScore());
            double normalizedTaste = normalizeDimension(interaction.tasteScore());
            double normalizedValue = normalizeDimension(interaction.valueScore());
            double normalizedMood = normalizeDimension(interaction.moodScore());

            restaurantBestScore.merge(interaction.restaurantId(), normalizedScore, Math::max);
            ownedRestaurantIds.add(interaction.restaurantId());
            regionTotals.merge(interaction.regionName(), normalizedScore, Double::sum);

            List<String> categories = categoryMap.getOrDefault(interaction.restaurantId(), List.of());
            if (!categories.isEmpty()) {
                double share = normalizedScore / categories.size();
                for (String category : categories) {
                    categoryTotals.merge(category, share, Double::sum);
                }
            }

            totalWeight += normalizedScore;
            tasteWeightedSum += normalizedTaste * normalizedScore;
            valueWeightedSum += normalizedValue * normalizedScore;
            moodWeightedSum += normalizedMood * normalizedScore;
        }

        ScoreVector scorePreference = totalWeight == 0.0
                ? ScoreVector.zero()
                : new ScoreVector(
                        clamp(tasteWeightedSum / totalWeight),
                        clamp(valueWeightedSum / totalWeight),
                        clamp(moodWeightedSum / totalWeight)
                );

        return new ListRecommendationUserProfile(
                Set.copyOf(ownedRestaurantIds),
                Map.copyOf(restaurantBestScore),
                normalizeDistribution(categoryTotals),
                normalizeDistribution(regionTotals),
                scorePreference,
                findDominantRegion(interactions)
        );
    }

    private String findDominantRegion(List<UserListInteractionRow> interactions) {
        Map<String, RegionPreferenceAccumulator> regionMap = new LinkedHashMap<>();
        for (UserListInteractionRow interaction : interactions) {
            regionMap.computeIfAbsent(interaction.regionName(), ignored -> new RegionPreferenceAccumulator())
                    .add(normalizeScore(interaction.autoScore()), interaction.updatedAt());
        }

        return regionMap.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, RegionPreferenceAccumulator> entry) -> entry.getValue().totalScore)
                        .reversed()
                        .thenComparing(entry -> entry.getValue().count, Comparator.reverseOrder())
                        .thenComparing((Map.Entry<String, RegionPreferenceAccumulator> entry) -> entry.getValue().latestUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<ListRecommendationFeature> loadCandidateFeatures(List<CandidateListSummaryRow> summaries) {
        if (summaries.isEmpty()) {
            return List.of();
        }

        List<Long> listIds = summaries.stream()
                .map(CandidateListSummaryRow::listId)
                .toList();
        List<CandidateListRestaurantRow> restaurantRows = listRecommendationRepository.findCandidateListRestaurants(listIds);
        Map<Long, List<CandidateListRestaurantRow>> restaurantRowsByListId = restaurantRows.stream()
                .collect(Collectors.groupingBy(
                        CandidateListRestaurantRow::listId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Long> restaurantIds = restaurantRows.stream()
                .map(CandidateListRestaurantRow::restaurantId)
                .distinct()
                .toList();
        Map<Long, List<String>> categoryMap = loadCategoryMap(restaurantIds);

        List<ListRecommendationFeature> features = new ArrayList<>();
        for (CandidateListSummaryRow summary : summaries) {
            List<CandidateListRestaurantRow> rows = restaurantRowsByListId.getOrDefault(summary.listId(), List.of());
            if (rows.size() < MIN_RESTAURANT_COUNT) {
                continue;
            }

            Map<Long, Double> restaurantWeight = buildRestaurantWeight(rows);
            Map<String, Double> categoryDistribution = buildCategoryDistribution(rows, categoryMap, restaurantWeight);
            ScoreVector scoreVector = buildScoreVector(rows, restaurantWeight);
            List<String> categorySummary = summarizeCategories(categoryDistribution);

            features.add(new ListRecommendationFeature(
                    summary.listId(),
                    summary.title(),
                    summary.description(),
                    summary.regionName(),
                    new OwnerFeature(summary.ownerId(), summary.ownerNickname(), summary.ownerProfileImageUrl()),
                    Math.toIntExact(summary.restaurantCount()),
                    Map.copyOf(restaurantWeight),
                    categoryDistribution,
                    scoreVector,
                    normalizeScore(summary.averageAutoScore()),
                    normalizeScore(summary.adjustedQualityScore()),
                    categorySummary
            ));
        }

        return features;
    }

    private Map<Long, Double> loadCollaborativeScoreMap(
            Long userId,
            ListRecommendationUserProfile profile,
            Set<Long> candidateOwnerIds
    ) {
        if (candidateOwnerIds.isEmpty() || profile.restaurantBestScore().isEmpty()) {
            return Map.of();
        }

        List<OwnerInteractionRow> ownerRows = listRecommendationRepository.findCandidateOwnerInteractions(
                userId,
                new ArrayList<>(candidateOwnerIds),
                MIN_COMMON_RESTAURANTS
        );
        if (ownerRows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, Double>> ownerScoreMap = new LinkedHashMap<>();
        for (OwnerInteractionRow row : ownerRows) {
            ownerScoreMap.computeIfAbsent(row.ownerId(), ignored -> new LinkedHashMap<>())
                    .put(row.restaurantId(), normalizeScore(row.autoScore()));
        }

        Map<Long, Double> collaborativeScoreMap = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : ownerScoreMap.entrySet()) {
            double similarity = cosineSimilarity(profile.restaurantBestScore(), entry.getValue());
            if (similarity > 0.0) {
                collaborativeScoreMap.put(entry.getKey(), similarity);
            }
        }
        return Map.copyOf(collaborativeScoreMap);
    }

    private double cosineSimilarity(Map<Long, Double> left, Map<Long, Double> right) {
        List<Long> commonRestaurantIds = left.keySet().stream()
                .filter(right::containsKey)
                .toList();
        if (commonRestaurantIds.size() < MIN_COMMON_RESTAURANTS) {
            return 0.0;
        }

        double numerator = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (Long restaurantId : commonRestaurantIds) {
            double leftScore = left.getOrDefault(restaurantId, 0.0);
            double rightScore = right.getOrDefault(restaurantId, 0.0);

            numerator += leftScore * rightScore;
            leftNorm += leftScore * leftScore;
            rightNorm += rightScore * rightScore;
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return clamp(numerator / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private List<ScoredListRecommendation> scoreCandidates(
            ListRecommendationUserProfile profile,
            List<ListRecommendationFeature> candidates,
            Map<Long, Double> collaborativeScoreMap,
            boolean fallbackRegion,
            double regionScore
    ) {
        return candidates.stream()
                .map(candidate -> new ScoredListRecommendation(
                        candidate,
                        listRecommendationScorer.score(
                                profile,
                                candidate,
                                collaborativeScoreMap.getOrDefault(candidate.owner().ownerId(), 0.0),
                                regionScore
                        ),
                        fallbackRegion
                ))
                .toList();
    }

    private Comparator<ScoredListRecommendation> recommendationComparator() {
        return Comparator
                .comparing((ScoredListRecommendation recommendation) -> recommendation.components().finalScore())
                .reversed()
                .thenComparing(recommendation -> recommendation.components().preferenceMatchScore(), Comparator.reverseOrder())
                .thenComparing(recommendation -> recommendation.components().qualityScore(), Comparator.reverseOrder())
                .thenComparing(recommendation -> recommendation.feature().restaurantCount(), Comparator.reverseOrder())
                .thenComparing(recommendation -> recommendation.feature().listId());
    }

    private List<ListRecommendationItemResponse> withRank(List<ListRecommendationItemResponse> items) {
        List<ListRecommendationItemResponse> rankedItems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ListRecommendationItemResponse item = items.get(index);
            rankedItems.add(new ListRecommendationItemResponse(
                    index + 1,
                    item.listId(),
                    item.title(),
                    item.description(),
                    item.regionName(),
                    item.owner(),
                    item.categorySummary(),
                    item.restaurantCount(),
                    item.recommendationScore(),
                    item.fallbackRegion(),
                    item.scoreDetails()
            ));
        }
        return rankedItems;
    }

    private Map<Long, Double> buildRestaurantWeight(List<CandidateListRestaurantRow> rows) {
        double totalScore = rows.stream()
                .map(CandidateListRestaurantRow::autoScore)
                .mapToDouble(this::normalizeScore)
                .sum();

        Map<Long, Double> weights = new LinkedHashMap<>();
        if (totalScore == 0.0) {
            double equalWeight = 1.0 / rows.size();
            for (CandidateListRestaurantRow row : rows) {
                weights.put(row.restaurantId(), equalWeight);
            }
            return weights;
        }

        for (CandidateListRestaurantRow row : rows) {
            weights.put(row.restaurantId(), normalizeScore(row.autoScore()) / totalScore);
        }
        return weights;
    }

    private Map<String, Double> buildCategoryDistribution(
            List<CandidateListRestaurantRow> rows,
            Map<Long, List<String>> categoryMap,
            Map<Long, Double> restaurantWeight
    ) {
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        for (CandidateListRestaurantRow row : rows) {
            List<String> categories = categoryMap.getOrDefault(row.restaurantId(), List.of());
            if (categories.isEmpty()) {
                continue;
            }

            double share = restaurantWeight.getOrDefault(row.restaurantId(), 0.0) / categories.size();
            for (String category : categories) {
                categoryTotals.merge(category, share, Double::sum);
            }
        }
        return normalizeDistribution(categoryTotals);
    }

    private ScoreVector buildScoreVector(
            List<CandidateListRestaurantRow> rows,
            Map<Long, Double> restaurantWeight
    ) {
        double taste = 0.0;
        double value = 0.0;
        double mood = 0.0;

        for (CandidateListRestaurantRow row : rows) {
            double weight = restaurantWeight.getOrDefault(row.restaurantId(), 0.0);
            taste += normalizeDimension(row.tasteScore()) * weight;
            value += normalizeDimension(row.valueScore()) * weight;
            mood += normalizeDimension(row.moodScore()) * weight;
        }

        return new ScoreVector(
                clamp(taste),
                clamp(value),
                clamp(mood)
        );
    }

    private List<String> summarizeCategories(Map<String, Double> categoryDistribution) {
        return categoryDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<Long, List<String>> loadCategoryMap(List<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> categoryMap = new LinkedHashMap<>();
        for (Restaurant restaurant : restaurantRepository.findAllById(restaurantIds)) {
            categoryMap.put(restaurant.getId(), restaurant.getCategoryNames());
        }
        return categoryMap;
    }

    private Map<String, Double> normalizeDistribution(Map<String, Double> totals) {
        if (totals.isEmpty()) {
            return Map.of();
        }

        double sum = totals.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        if (sum == 0.0) {
            return totals.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, ignored -> 0.0));
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / sum);
        }
        return Map.copyOf(normalized);
    }

    private Set<Long> mergeOwnerIds(
            List<ListRecommendationFeature> sameRegionFeatures,
            List<ListRecommendationFeature> fallbackFeatures
    ) {
        Set<Long> ownerIds = new LinkedHashSet<>();
        ownerIds.addAll(sameRegionFeatures.stream().map(feature -> feature.owner().ownerId()).toList());
        ownerIds.addAll(fallbackFeatures.stream().map(feature -> feature.owner().ownerId()).toList());
        return ownerIds;
    }

    private double normalizeScore(BigDecimal autoScore) {
        if (autoScore == null) {
            return 0.0;
        }
        return clamp(autoScore.doubleValue() / 100.0);
    }

    private double normalizeDimension(BigDecimal score) {
        if (score == null) {
            return 0.0;
        }
        return clamp(score.doubleValue() / 10.0);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private final class RegionPreferenceAccumulator {
        private double totalScore;
        private int count;
        private LocalDateTime latestUpdatedAt;

        private void add(double score, LocalDateTime updatedAt) {
            this.totalScore += score;
            this.count++;
            if (updatedAt != null && (this.latestUpdatedAt == null || updatedAt.isAfter(this.latestUpdatedAt))) {
                this.latestUpdatedAt = updatedAt;
            }
        }
    }

    private record ScoredListRecommendation(
            ListRecommendationFeature feature,
            ListRecommendationScoreComponents components,
            boolean fallbackRegion
    ) {
        private ListRecommendationItemResponse toResponse() {
            return new ListRecommendationItemResponse(
                    0,
                    feature.listId(),
                    feature.title(),
                    feature.description(),
                    feature.regionName(),
                    new RecommendationOwnerResponse(
                            feature.owner().ownerId(),
                            feature.owner().nickname(),
                            feature.owner().profileImageUrl()
                    ),
                    feature.categorySummary(),
                    feature.restaurantCount(),
                    scale(components.finalScore()),
                    fallbackRegion,
                    new ListRecommendationScoreDetailResponse(
                            scale(components.preferenceMatchScore()),
                            scale(components.restaurantMatchScore()),
                            scale(components.categoryMatchScore()),
                            scale(components.scoreStyleMatchScore()),
                            scale(components.qualityScore()),
                            scale(components.adjustedQualityScore()),
                            scale(components.sizeConfidenceScore()),
                            scale(components.collaborativeScore()),
                            scale(components.regionScore())
                    )
            );
        }

        private BigDecimal scale(double value) {
            return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
        }
    }
}
