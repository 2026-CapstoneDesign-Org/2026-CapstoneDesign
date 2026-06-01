package com.example.Capstone.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.client.PcmapSearchClient;
import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.example.Capstone.dto.response.SearchRegionItemResponse;
import com.example.Capstone.dto.response.SearchResponse;
import com.example.Capstone.dto.response.SearchRestaurantItemResponse;
import com.example.Capstone.dto.response.SearchUserItemResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.repository.RestaurantRankingRow;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;
import com.example.Capstone.service.search.support.SearchInterpretation;
import com.example.Capstone.service.search.support.SearchQueryInterpreter;
import com.example.Capstone.service.search.support.SearchResultMapper;
import com.example.Capstone.service.search.support.SearchRestaurantMatcher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final String PRIMARY_TYPE_RESTAURANT = "RESTAURANT";
    private static final String PRIMARY_TYPE_USER = "USER";
    private static final String PRIMARY_TYPE_REGION = "REGION";

    private static final String SOURCE_INTERNAL = "INTERNAL";
    private static final String SOURCE_EXTERNAL_FALLBACK = "EXTERNAL_FALLBACK";

    private static final int RESTAURANT_RESULT_LIMIT = 10;
    private static final int USER_RESULT_LIMIT = 10;
    private static final int REGION_RESULT_LIMIT = 10;
    private static final int INTERNAL_CANDIDATE_FETCH_LIMIT = 300;
    private static final int EXTERNAL_FALLBACK_LIMIT = 5;
    private static final int RANKING_SIGNAL_LIMIT = 100;
    private static final int RANKING_SMOOTHING_CONSTANT = 5;

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final PcmapSearchClient pcmapSearchClient;

    public SearchResponse search(String query) {
        String normalizedQuery = SearchQueryInterpreter.normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            throw new BusinessException("query는 비어 있을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        SearchInterpretation interpretation = new SearchQueryInterpreter(restaurantRepository).interpret(normalizedQuery);
        List<Restaurant> internalCandidates = loadInternalRestaurantCandidates(interpretation);
        List<SearchUserItemResponse> userItems = searchUserItems(interpretation);
        List<SearchRestaurantItemResponse> restaurantItems = searchRestaurantItems(
                interpretation,
                internalCandidates,
                userItems.isEmpty()
        );

        boolean fallbackUsed = restaurantItems.stream()
                .anyMatch(item -> SOURCE_EXTERNAL_FALLBACK.equals(item.source()));

        SearchInterpretation finalizedInterpretation = interpretation.withFallbackUsed(fallbackUsed);
        List<SearchRegionItemResponse> regionItems = searchRegionItems(finalizedInterpretation);

        return new SearchResponse(
                normalizedQuery,
                decidePrimaryType(finalizedInterpretation, restaurantItems, userItems, regionItems),
                SearchResultMapper.toInterpretationResponse(finalizedInterpretation),
                restaurantItems.size(),
                userItems.size(),
                regionItems.size(),
                restaurantItems,
                userItems,
                regionItems
        );
    }

    private List<SearchRestaurantItemResponse> searchRestaurantItems(
            SearchInterpretation interpretation,
            List<Restaurant> internalCandidates,
            boolean userItemsEmpty
    ) {
        if (interpretation.explicitUserQuery()) {
            return List.of();
        }

        Map<Long, Integer> rankingOrder = loadRankingOrder(interpretation, internalCandidates);

        List<SearchRestaurantItemResponse> internalItems = internalCandidates.stream()
                .map(candidate -> mapInternalRestaurantItem(candidate, interpretation))
                .filter(Objects::nonNull)
                .sorted(internalResultComparator(rankingOrder))
                .limit(RESTAURANT_RESULT_LIMIT)
                .toList();

        if (!shouldUseFallback(interpretation, internalItems.size(), userItemsEmpty)) {
            return internalItems;
        }

        List<SearchRestaurantItemResponse> mergedItems = new ArrayList<>(internalItems);
        mergedItems.addAll(loadExternalFallbackItems(interpretation, internalCandidates));
        return mergedItems.stream()
                .limit(RESTAURANT_RESULT_LIMIT)
                .toList();
    }

    private List<Restaurant> loadInternalRestaurantCandidates(SearchInterpretation interpretation) {
        List<Restaurant> candidates = new ArrayList<>();
        PageRequest candidatePage = PageRequest.of(0, INTERNAL_CANDIDATE_FETCH_LIMIT);

        if (interpretation.restaurantKeyword() != null) {
            if (interpretation.regionKeyword() != null) {
                addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByRegionAndSearchTokens(
                        interpretation.regionKeyword(),
                        interpretation.restaurantKeyword(),
                        candidatePage
                ));
            }
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsBySearchTokens(
                    interpretation.restaurantKeyword(),
                    candidatePage
            ));
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsBySearchKeyword(
                    interpretation.restaurantKeyword(),
                    candidatePage
            ));
        } else if (interpretation.regionKeyword() != null) {
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByRegionSignal(
                    interpretation.regionKeyword(),
                    candidatePage
            ));
        } else {
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsBySearchTokens(
                    interpretation.normalizedQuery(),
                    candidatePage
            ));
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsBySearchKeyword(
                    interpretation.normalizedQuery(),
                    candidatePage
            ));
        }

        LinkedHashMap<Long, Restaurant> deduplicated = new LinkedHashMap<>();
        for (Restaurant candidate : candidates) {
            if (interpretation.regionKeyword() != null
                    && !SearchRestaurantMatcher.matchesRegion(candidate, interpretation.regionKeyword())) {
                continue;
            }
            deduplicated.putIfAbsent(candidate.getId(), candidate);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private void addCandidates(List<Restaurant> target, List<Restaurant> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        target.addAll(candidates);
    }

    private SearchRestaurantItemResponse mapInternalRestaurantItem(
            Restaurant restaurant,
            SearchInterpretation interpretation
    ) {
        String matchedBy = SearchRestaurantMatcher.resolveMatchedBy(restaurant, interpretation);
        if (matchedBy == null) {
            return null;
        }

        return SearchResultMapper.toInternalRestaurantItem(restaurant, matchedBy, SOURCE_INTERNAL);
    }

    private boolean shouldUseFallback(SearchInterpretation interpretation, int internalCount, boolean userItemsEmpty) {
        return internalCount == 0
                && userItemsEmpty
                && interpretation.restaurantKeyword() != null
                && !interpretation.genericBrowseQuery();
    }

    private Comparator<SearchRestaurantItemResponse> internalResultComparator(Map<Long, Integer> rankingOrder) {
        if (!rankingOrder.isEmpty()) {
            return Comparator
                    .comparingInt((SearchRestaurantItemResponse item) ->
                            rankingOrder.getOrDefault(item.restaurantId(), Integer.MAX_VALUE))
                    .thenComparingInt(item -> SearchRestaurantMatcher.matchPriority(item.matchedBy()))
                    .thenComparing(SearchRestaurantItemResponse::restaurantName, Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(SearchRestaurantItemResponse::restaurantId, Comparator.nullsLast(Long::compareTo));
        }
        return Comparator
                .comparingInt((SearchRestaurantItemResponse item) -> SearchRestaurantMatcher.matchPriority(item.matchedBy()))
                .thenComparing(SearchRestaurantItemResponse::restaurantName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(SearchRestaurantItemResponse::restaurantId, Comparator.nullsLast(Long::compareTo));
    }

    private Map<Long, Integer> loadRankingOrder(
            SearchInterpretation interpretation,
            List<Restaurant> internalCandidates
    ) {
        if (internalCandidates == null || internalCandidates.isEmpty()) {
            return Map.of();
        }

        String regionName = interpretation.regionKeyword();
        String category = resolveRankingCategory(interpretation.restaurantKeyword());
        List<RestaurantRankingRow> rows = safeRankingRows(regionName, category);
        if (rows.isEmpty() && category != null) {
            rows = safeRankingRows(regionName, null);
        }
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> rankingOrder = new HashMap<>();
        for (int index = 0; index < rows.size(); index += 1) {
            rankingOrder.putIfAbsent(rows.get(index).restaurantId(), index);
        }
        return rankingOrder;
    }

    private List<RestaurantRankingRow> safeRankingRows(String regionName, String category) {
        List<RestaurantRankingRow> rows = restaurantRepository.findRestaurantRankings(
                regionName,
                category,
                RANKING_SIGNAL_LIMIT,
                RANKING_SMOOTHING_CONSTANT
        );
        return rows == null ? List.of() : rows;
    }

    private String resolveRankingCategory(String restaurantKeyword) {
        if (restaurantKeyword == null || restaurantKeyword.isBlank()) {
            return null;
        }
        List<String> tokens = SearchRestaurantMatcher.tokenize(restaurantKeyword);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.get(tokens.size() - 1);
    }

    private List<SearchRestaurantItemResponse> loadExternalFallbackItems(
            SearchInterpretation interpretation,
            List<Restaurant> internalCandidates
    ) {
        String fallbackKeyword = buildFallbackKeyword(interpretation);
        if (fallbackKeyword == null) {
            return List.of();
        }

        Set<String> dedupKeys = new LinkedHashSet<>();
        for (Restaurant restaurant : internalCandidates) {
            if (restaurant.getPcmapPlaceId() != null && !restaurant.getPcmapPlaceId().isBlank()) {
                dedupKeys.add("place:" + restaurant.getPcmapPlaceId());
            }
            addInternalDedupKey(dedupKeys, restaurant.getName(), restaurant.getAddress());
            addInternalDedupKey(dedupKeys, restaurant.getName(), restaurant.getRoadAddress());
        }

        List<SearchRestaurantItemResponse> items = new ArrayList<>();
        List<PcmapRestaurantCandidate> candidates = pcmapSearchClient.searchRestaurants(
                fallbackKeyword,
                RESTAURANT_RESULT_LIMIT + EXTERNAL_FALLBACK_LIMIT
        );

        for (PcmapRestaurantCandidate candidate : candidates) {
            if (interpretation.regionKeyword() != null
                    && !matchesExternalRegion(candidate, interpretation.regionKeyword())) {
                continue;
            }

            String placeKey = candidate.placeId() == null ? null : "place:" + candidate.placeId();
            String nameAddressKey = "name-address:" + normalizeForDedup(candidate.name())
                    + "|" + normalizeForDedup(resolveExternalAddress(candidate));

            if ((placeKey != null && dedupKeys.contains(placeKey)) || dedupKeys.contains(nameAddressKey)) {
                continue;
            }

            if (placeKey != null) {
                dedupKeys.add(placeKey);
            }
            dedupKeys.add(nameAddressKey);

            items.add(SearchResultMapper.toExternalRestaurantItem(
                    candidate,
                    interpretation.regionKeyword(),
                    SOURCE_EXTERNAL_FALLBACK,
                    SearchRestaurantMatcher.MATCH_EXTERNAL_FALLBACK,
                    resolveExternalAddress(candidate)
            ));

            if (items.size() >= EXTERNAL_FALLBACK_LIMIT) {
                break;
            }
        }

        return items;
    }

    private String buildFallbackKeyword(SearchInterpretation interpretation) {
        if (interpretation.restaurantKeyword() == null) {
            return null;
        }
        if (interpretation.regionKeyword() == null) {
            return interpretation.restaurantKeyword();
        }
        return interpretation.regionKeyword() + " " + interpretation.restaurantKeyword();
    }

    private String resolveExternalAddress(PcmapRestaurantCandidate candidate) {
        if (candidate.roadAddress() != null && !candidate.roadAddress().isBlank()) {
            return candidate.roadAddress();
        }
        if (candidate.address() != null && !candidate.address().isBlank()) {
            return candidate.address();
        }
        return candidate.fullAddress();
    }

    private boolean matchesExternalRegion(PcmapRestaurantCandidate candidate, String regionKeyword) {
        return SearchRestaurantMatcher.containsIgnoreCase(candidate.address(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(candidate.roadAddress(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(candidate.fullAddress(), regionKeyword);
    }

    private List<SearchUserItemResponse> searchUserItems(SearchInterpretation interpretation) {
        if (interpretation.explicitRegionQuery() || interpretation.regionKeyword() != null) {
            return List.of();
        }

        String userKeyword = interpretation.userKeyword();
        if (userKeyword == null || userKeyword.isBlank()) {
            return List.of();
        }

        return userRepository.searchVisibleUsers(userKeyword, PageRequest.of(0, USER_RESULT_LIMIT)).stream()
                .map(SearchResultMapper::toUserItem)
                .toList();
    }

    private List<SearchRegionItemResponse> searchRegionItems(SearchInterpretation interpretation) {
        String regionKeyword = interpretation.regionKeyword();
        if (regionKeyword == null || regionKeyword.isBlank()) {
            return List.of();
        }

        LinkedHashMap<String, SearchRegionItemResponse> deduplicated = new LinkedHashMap<>();
        List<Restaurant> candidates = restaurantRepository.searchVisibleRestaurantsByRegionSignal(
                regionKeyword,
                PageRequest.of(0, INTERNAL_CANDIDATE_FETCH_LIMIT)
        );

        for (Restaurant candidate : candidates) {
            if (!SearchRestaurantMatcher.matchesRegion(candidate, regionKeyword)) {
                continue;
            }

            deduplicated.putIfAbsent(
                    candidate.getRegionName(),
                    SearchResultMapper.toRegionItem(
                            candidate.getRegionName(),
                            SearchRestaurantMatcher.resolveRegionDisplayName(candidate, regionKeyword),
                            regionKeyword
                    )
            );
            if (deduplicated.size() >= REGION_RESULT_LIMIT) {
                break;
            }
        }

        return new ArrayList<>(deduplicated.values());
    }

    private String decidePrimaryType(
            SearchInterpretation interpretation,
            List<SearchRestaurantItemResponse> restaurants,
            List<SearchUserItemResponse> users,
            List<SearchRegionItemResponse> regions
    ) {
        if (interpretation.explicitUserQuery()) {
            return PRIMARY_TYPE_USER;
        }

        if (interpretation.explicitRegionQuery()) {
            if (interpretation.restaurantKeyword() != null
                    && !interpretation.genericBrowseQuery()
                    && !restaurants.isEmpty()) {
                return PRIMARY_TYPE_RESTAURANT;
            }
            return PRIMARY_TYPE_REGION;
        }

        if (interpretation.regionKeyword() != null
                && interpretation.restaurantKeyword() != null
                && !restaurants.isEmpty()) {
            return PRIMARY_TYPE_RESTAURANT;
        }

        if (interpretation.genericBrowseQuery() && !regions.isEmpty()) {
            return PRIMARY_TYPE_REGION;
        }

        if (restaurants.isEmpty() && !users.isEmpty() && regions.isEmpty()) {
            return PRIMARY_TYPE_USER;
        }

        if (restaurants.isEmpty() && !regions.isEmpty()) {
            return PRIMARY_TYPE_REGION;
        }

        return PRIMARY_TYPE_RESTAURANT;
    }

    private String normalizeForDedup(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    private void addInternalDedupKey(Set<String> dedupKeys, String name, String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        dedupKeys.add("name-address:" + normalizeForDedup(name)
                + "|" + normalizeForDedup(address));
    }
}
