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
import com.example.Capstone.dto.response.RestaurantRecommendationItemResponse;
import com.example.Capstone.dto.response.RestaurantRecommendationResponse;
import com.example.Capstone.recommendation.model.restaurant.LikedRestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.NeighborProfile;
import com.example.Capstone.recommendation.model.restaurant.RecommendationScoreComponents;
import com.example.Capstone.recommendation.model.restaurant.RestaurantFeature;
import com.example.Capstone.recommendation.model.restaurant.UserPreferenceProfile;
import com.example.Capstone.recommendation.scorer.RestaurantRecommendationScorer;
import com.example.Capstone.repository.RestaurantRecommendationRepository;
import com.example.Capstone.repository.RestaurantRecommendationRepository.CandidateRestaurantRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.NeighborInteractionRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.RankingSignalRow;
import com.example.Capstone.repository.RestaurantRecommendationRepository.UserInteractionRow;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantRecommendationService {

    private static final int RESULT_LIMIT = 4;
    private static final int USER_TOP_N = 10;
    private static final int SAME_REGION_CANDIDATE_LIMIT = 200;
    private static final int FALLBACK_CANDIDATE_LIMIT = 100;
    private static final int PRELIMINARY_NEIGHBOR_LIMIT = 50;
    private static final int FINAL_NEIGHBOR_LIMIT = 20;
    private static final int MIN_COMMON_RESTAURANTS = 2;
    private static final int SMOOTHING_CONSTANT = 5;
    private static final double SAME_REGION_SCORE = 1.0;
    private static final double FALLBACK_REGION_SCORE = 0.55;

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantRecommendationRepository restaurantRecommendationRepository;
    private final RestaurantRecommendationScorer restaurantRecommendationScorer;

    public RestaurantRecommendationResponse getRestaurantRecommendations(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다."));

        UserPreferenceProfile profile = buildUserPreferenceProfile(userId);
        if (profile.dominantRegion() == null) {
            return RestaurantRecommendationResponse.of(LocalDateTime.now(), null, RESULT_LIMIT, List.of());
        }

        List<CandidateRestaurantRow> sameRegionCandidates = restaurantRecommendationRepository.findSameRegionCandidates(
                userId,
                profile.dominantRegion(),
                SAME_REGION_CANDIDATE_LIMIT,
                SMOOTHING_CONSTANT
        );

        List<CandidateRestaurantRow> fallbackCandidates = List.of();
        if (sameRegionCandidates.size() < RESULT_LIMIT) {
            fallbackCandidates = restaurantRecommendationRepository.findFallbackCandidates(
                    userId,
                    profile.dominantRegion(),
                    FALLBACK_CANDIDATE_LIMIT,
                    SMOOTHING_CONSTANT
            );
        }

        Map<Long, RankingSignalRow> rankingSignalMap = loadRankingSignalMap(sameRegionCandidates, fallbackCandidates);
        List<RestaurantFeature> sameRegionFeatures = toRestaurantFeatures(sameRegionCandidates, rankingSignalMap);
        List<RestaurantFeature> fallbackFeatures = toRestaurantFeatures(fallbackCandidates, rankingSignalMap);

        Map<Long, Double> collaborativeScoreMap = loadCollaborativeScoreMap(
                userId,
                profile,
                mergeCandidateIds(sameRegionFeatures, fallbackFeatures)
        );

        List<ScoredRecommendation> scoredRecommendations = new ArrayList<>();
        scoredRecommendations.addAll(scoreCandidates(profile, sameRegionFeatures, collaborativeScoreMap, false, SAME_REGION_SCORE));
        scoredRecommendations.addAll(scoreCandidates(profile, fallbackFeatures, collaborativeScoreMap, true, FALLBACK_REGION_SCORE));

        List<RestaurantRecommendationItemResponse> items = scoredRecommendations.stream()
                .sorted(recommendationComparator())
                .limit(RESULT_LIMIT)
                .map(ScoredRecommendation::toResponse)
                .toList();

        return RestaurantRecommendationResponse.of(
                LocalDateTime.now(),
                profile.dominantRegion(),
                RESULT_LIMIT,
                withRank(items)
        );
    }

    private UserPreferenceProfile buildUserPreferenceProfile(Long userId) {
        List<UserInteractionRow> interactions = restaurantRecommendationRepository.findUserInteractions(userId);
        if (interactions.isEmpty()) {
            return UserPreferenceProfile.empty();
        }

        List<Long> restaurantIds = interactions.stream()
                .map(UserInteractionRow::restaurantId)
                .toList();
        Map<Long, List<String>> categoryMap = loadCategoryMap(restaurantIds);

        Map<Long, Double> restaurantBestScore = new LinkedHashMap<>();
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        Map<String, Double> regionTotals = new LinkedHashMap<>();
        Set<Long> ownedRestaurantIds = new LinkedHashSet<>();

        for (UserInteractionRow interaction : interactions) {
            double normalizedScore = normalizeScore(interaction.autoScore());

            restaurantBestScore.put(interaction.restaurantId(), normalizedScore);
            ownedRestaurantIds.add(interaction.restaurantId());
            regionTotals.merge(interaction.regionName(), normalizedScore, Double::sum);

            for (String category : categoryMap.getOrDefault(interaction.restaurantId(), List.of())) {
                categoryTotals.merge(category, normalizedScore, Double::sum);
            }
        }

        List<LikedRestaurantFeature> topLikedRestaurants = interactions.stream()
                .sorted(Comparator
                        .comparing((UserInteractionRow row) -> normalizeScore(row.autoScore())).reversed()
                        .thenComparing(UserInteractionRow::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UserInteractionRow::restaurantId))
                .limit(USER_TOP_N)
                .map(row -> new LikedRestaurantFeature(
                        row.restaurantId(),
                        normalizeScore(row.autoScore()),
                        row.regionName(),
                        Set.copyOf(categoryMap.getOrDefault(row.restaurantId(), List.of())),
                        row.updatedAt()
                ))
                .toList();

        return new UserPreferenceProfile(
                Set.copyOf(ownedRestaurantIds),
                Map.copyOf(restaurantBestScore),
                normalizeDistribution(categoryTotals),
                normalizeDistribution(regionTotals),
                topLikedRestaurants,
                findDominantRegion(interactions)
        );
    }

    private String findDominantRegion(List<UserInteractionRow> interactions) {
        Map<String, RegionPreferenceAccumulator> regionMap = new LinkedHashMap<>();
        for (UserInteractionRow interaction : interactions) {
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

    private Map<Long, RankingSignalRow> loadRankingSignalMap(
            List<CandidateRestaurantRow> sameRegionCandidates,
            List<CandidateRestaurantRow> fallbackCandidates
    ) {
        List<Long> candidateIds = new ArrayList<>();
        candidateIds.addAll(sameRegionCandidates.stream().map(CandidateRestaurantRow::restaurantId).toList());
        candidateIds.addAll(fallbackCandidates.stream().map(CandidateRestaurantRow::restaurantId).toList());

        return restaurantRecommendationRepository.findRankingSignals(candidateIds, SMOOTHING_CONSTANT).stream()
                .collect(Collectors.toMap(RankingSignalRow::restaurantId, row -> row));
    }

    private List<RestaurantFeature> toRestaurantFeatures(
            List<CandidateRestaurantRow> candidateRows,
            Map<Long, RankingSignalRow> rankingSignalMap
    ) {
        if (candidateRows.isEmpty()) {
            return List.of();
        }

        List<Long> restaurantIds = candidateRows.stream()
                .map(CandidateRestaurantRow::restaurantId)
                .toList();
        Map<Long, List<String>> categoryMap = loadCategoryMap(restaurantIds);

        return candidateRows.stream()
                .map(row -> {
                    RankingSignalRow rankingSignal = rankingSignalMap.get(row.restaurantId());
                    double rankingAdjustmentScore = rankingSignal == null ? 0.0 : normalizeScore(rankingSignal.adjustedScore());
                    long evaluationCount = rankingSignal == null || rankingSignal.evaluationCount() == null
                            ? 0L
                            : rankingSignal.evaluationCount();

                    return new RestaurantFeature(
                            row.restaurantId(),
                            row.restaurantName(),
                            row.address(),
                            row.regionName(),
                            row.imageUrl(),
                            categoryMap.getOrDefault(row.restaurantId(), List.of()),
                            rankingAdjustmentScore,
                            evaluationCount
                    );
                })
                .toList();
    }

    private Map<Long, Double> loadCollaborativeScoreMap(
            Long userId,
            UserPreferenceProfile profile,
            Set<Long> candidateIds
    ) {
        if (candidateIds.isEmpty() || profile.restaurantBestScore().isEmpty()) {
            return Map.of();
        }

        List<NeighborInteractionRow> neighborRows = restaurantRecommendationRepository.findNeighborInteractions(
                userId,
                MIN_COMMON_RESTAURANTS,
                PRELIMINARY_NEIGHBOR_LIMIT
        );
        if (neighborRows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<Long, Double>> neighborScoreMap = new LinkedHashMap<>();
        for (NeighborInteractionRow row : neighborRows) {
            neighborScoreMap.computeIfAbsent(row.userId(), ignored -> new LinkedHashMap<>())
                    .put(row.restaurantId(), normalizeScore(row.autoScore()));
        }

        List<NeighborProfile> neighbors = neighborScoreMap.entrySet().stream()
                .map(entry -> toNeighborProfile(entry.getKey(), entry.getValue(), profile.restaurantBestScore()))
                .filter(neighbor -> neighbor.similarity() > 0.0)
                .sorted(Comparator.comparingDouble(NeighborProfile::similarity).reversed()
                        .thenComparing(NeighborProfile::userId))
                .limit(FINAL_NEIGHBOR_LIMIT)
                .toList();

        if (neighbors.isEmpty()) {
            return Map.of();
        }

        Map<Long, Double> collaborativeScoreMap = new LinkedHashMap<>();
        for (Long candidateId : candidateIds) {
            double numerator = 0.0;
            double denominator = 0.0;

            for (NeighborProfile neighbor : neighbors) {
                Double neighborScore = neighbor.restaurantBestScore().get(candidateId);
                if (neighborScore == null) {
                    continue;
                }
                numerator += neighbor.similarity() * neighborScore;
                denominator += neighbor.similarity();
            }

            if (denominator > 0.0) {
                collaborativeScoreMap.put(candidateId, numerator / denominator);
            }
        }

        return collaborativeScoreMap;
    }

    private NeighborProfile toNeighborProfile(
            Long neighborUserId,
            Map<Long, Double> neighborScores,
            Map<Long, Double> currentUserScores
    ) {
        return new NeighborProfile(
                neighborUserId,
                cosineSimilarity(currentUserScores, neighborScores),
                Map.copyOf(neighborScores)
        );
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

        return numerator / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<ScoredRecommendation> scoreCandidates(
            UserPreferenceProfile profile,
            List<RestaurantFeature> candidates,
            Map<Long, Double> collaborativeScoreMap,
            boolean fallbackRegion,
            double regionScore
    ) {
        return candidates.stream()
                .map(candidate -> new ScoredRecommendation(
                        candidate,
                        restaurantRecommendationScorer.score(
                                profile,
                                candidate,
                                collaborativeScoreMap.getOrDefault(candidate.restaurantId(), 0.0),
                                regionScore
                        ),
                        fallbackRegion
                ))
                .toList();
    }

    private Comparator<ScoredRecommendation> recommendationComparator() {
        return Comparator
                .comparing((ScoredRecommendation recommendation) -> recommendation.components().finalScore())
                .reversed()
                .thenComparing(recommendation -> recommendation.components().rankingAdjustmentScore(), Comparator.reverseOrder())
                .thenComparing(recommendation -> recommendation.feature().evaluationCount(), Comparator.reverseOrder())
                .thenComparing(recommendation -> recommendation.feature().restaurantId());
    }

    private List<RestaurantRecommendationItemResponse> withRank(List<RestaurantRecommendationItemResponse> items) {
        List<RestaurantRecommendationItemResponse> rankedItems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            RestaurantRecommendationItemResponse item = items.get(index);
            rankedItems.add(new RestaurantRecommendationItemResponse(
                    index + 1,
                    item.restaurantId(),
                    item.restaurantName(),
                    item.regionName(),
                    item.imageUrl(),
                    item.categories(),
                    item.finalScore(),
                    item.userPreferenceScore(),
                    item.categoryFitScore(),
                    item.rankingAdjustmentScore(),
                    item.collaborativeScore(),
                    item.regionScore(),
                    item.fallbackRegion()
            ));
        }
        return rankedItems;
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

        double sum = totals.values().stream().mapToDouble(Double::doubleValue).sum();
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

    private Set<Long> mergeCandidateIds(List<RestaurantFeature> sameRegionFeatures, List<RestaurantFeature> fallbackFeatures) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(sameRegionFeatures.stream().map(RestaurantFeature::restaurantId).toList());
        candidateIds.addAll(fallbackFeatures.stream().map(RestaurantFeature::restaurantId).toList());
        return candidateIds;
    }

    private double normalizeScore(BigDecimal autoScore) {
        if (autoScore == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, autoScore.doubleValue() / 100.0));
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

    private record ScoredRecommendation(
            RestaurantFeature feature,
            RecommendationScoreComponents components,
            boolean fallbackRegion
    ) {
        private RestaurantRecommendationItemResponse toResponse() {
            return new RestaurantRecommendationItemResponse(
                    0,
                    feature.restaurantId(),
                    feature.restaurantName(),
                    feature.regionName(),
                    feature.imageUrl(),
                    feature.categories(),
                    scale(components.finalScore()),
                    scale(components.userPreferenceScore()),
                    scale(components.categoryFitScore()),
                    scale(components.rankingAdjustmentScore()),
                    scale(components.collaborativeScore()),
                    scale(components.regionScore()),
                    fallbackRegion
            );
        }

        private BigDecimal scale(double value) {
            return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
        }
    }
}
