package com.example.Capstone.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.example.Capstone.service.support.RestaurantCategoryResolver;
import com.example.Capstone.service.support.RestaurantRegionResolver;
import com.example.Capstone.service.support.RestaurantRegionResolver.RegionSchema;

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
    private static final int PER_TOKEN_CANDIDATE_FETCH_LIMIT = 120;
    private static final int EXTERNAL_FALLBACK_LIMIT = 5;
    private static final int FALLBACK_MIN_INTERNAL_RESULTS = 5;
    private static final int RANKING_SIGNAL_LIMIT = 100;
    private static final int RANKING_SMOOTHING_CONSTANT = 5;

    private static final String FALLBACK_REASON_NO_INTERNAL_RESULTS = "NO_INTERNAL_RESULTS";
    private static final String FALLBACK_REASON_LOW_INTERNAL_RESULT_COUNT = "LOW_INTERNAL_RESULT_COUNT";
    private static final String FALLBACK_REASON_WEAK_INTERNAL_MATCH = "WEAK_INTERNAL_MATCH";

    private static final Set<String> BROAD_EXTERNAL_FALLBACK_KEYWORDS = Set.of(
            "맛집", "식당", "음식점", "밥집", "추천", "근처", "주변",
            "한식", "중식", "일식", "양식", "분식", "카페", "고기"
    );

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final PcmapSearchClient pcmapSearchClient;

    @Transactional
    public SearchResponse search(String query) {
        String normalizedQuery = SearchQueryInterpreter.normalizeQuery(query);
        if (normalizedQuery.isBlank()) {
            throw new BusinessException("query는 비어 있을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        SearchInterpretation interpretation = new SearchQueryInterpreter(restaurantRepository).interpret(normalizedQuery);
        List<Restaurant> internalCandidates = loadInternalRestaurantCandidates(interpretation);
        List<SearchUserItemResponse> userItems = searchUserItems(interpretation);
        RestaurantSearchResult restaurantSearchResult = searchRestaurantItems(
                interpretation,
                internalCandidates,
                userItems.isEmpty()
        );
        List<SearchRestaurantItemResponse> restaurantItems = restaurantSearchResult.items();

        SearchInterpretation finalizedInterpretation = interpretation.withFallbackDecision(
                restaurantSearchResult.fallbackUsed(),
                restaurantSearchResult.fallbackAttempted(),
                restaurantSearchResult.fallbackReason(),
                restaurantSearchResult.fallbackResultCount()
        );
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

    private RestaurantSearchResult searchRestaurantItems(
            SearchInterpretation interpretation,
            List<Restaurant> internalCandidates,
            boolean userItemsEmpty
    ) {
        if (interpretation.explicitUserQuery()) {
            return new RestaurantSearchResult(List.of(), false, false, null, 0);
        }

        Map<Long, Integer> rankingOrder = loadRankingOrder(interpretation, internalCandidates);

        List<SearchRestaurantItemResponse> internalItems = internalCandidates.stream()
                .map(candidate -> mapInternalRestaurantItem(candidate, interpretation))
                .filter(Objects::nonNull)
                .sorted(internalResultComparator(rankingOrder))
                .limit(RESTAURANT_RESULT_LIMIT)
                .toList();

        String fallbackReason = resolveFallbackReason(interpretation, internalItems, userItemsEmpty);
        if (fallbackReason == null) {
            return new RestaurantSearchResult(internalItems, false, false, null, 0);
        }

        List<SearchRestaurantItemResponse> mergedItems = new ArrayList<>(internalItems);
        List<SearchRestaurantItemResponse> fallbackItems = loadExternalFallbackItems(interpretation, internalCandidates);
        mergedItems.addAll(fallbackItems);
        return new RestaurantSearchResult(mergedItems.stream()
                .limit(RESTAURANT_RESULT_LIMIT)
                .toList(), !fallbackItems.isEmpty(), true, fallbackReason, fallbackItems.size());
    }

    private List<Restaurant> loadInternalRestaurantCandidates(SearchInterpretation interpretation) {
        List<Restaurant> candidates = new ArrayList<>();
        PageRequest candidatePage = PageRequest.of(0, INTERNAL_CANDIDATE_FETCH_LIMIT);
        PageRequest tokenPage = PageRequest.of(0, PER_TOKEN_CANDIDATE_FETCH_LIMIT);

        if (interpretation.restaurantKeyword() != null) {
            if (interpretation.regionKeyword() != null) {
                addKeywordCandidates(candidates, interpretation.regionKeyword(), interpretation.restaurantKeyword(), candidatePage);
                addTokenCandidates(candidates, interpretation.regionKeyword(), interpretation.restaurantKeyword(), tokenPage);
            } else {
                addKeywordCandidates(candidates, null, interpretation.restaurantKeyword(), candidatePage);
                addTokenCandidates(candidates, null, interpretation.restaurantKeyword(), tokenPage);
            }
        } else if (interpretation.regionKeyword() != null) {
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByRegionSignal(
                    interpretation.regionKeyword(),
                    candidatePage
            ));
        } else {
            addKeywordCandidates(candidates, null, interpretation.normalizedQuery(), candidatePage);
            addTokenCandidates(candidates, null, interpretation.normalizedQuery(), tokenPage);
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

    private void addKeywordCandidates(
            List<Restaurant> candidates,
            String regionKeyword,
            String keyword,
            PageRequest page
    ) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        List<Restaurant> coreCandidates;
        if (regionKeyword == null) {
            coreCandidates = restaurantRepository.searchVisibleRestaurantsByCoreKeyword(keyword, page);
        } else {
            coreCandidates = restaurantRepository.searchVisibleRestaurantsByRegionAndCoreKeyword(
                    regionKeyword,
                    keyword,
                    page
            );
        }
        addCandidates(candidates, coreCandidates);

        if (hasNameMatch(coreCandidates, keyword) || candidates.size() >= INTERNAL_CANDIDATE_FETCH_LIMIT) {
            return;
        }

        if (regionKeyword == null) {
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByMenuKeyword(keyword, page));
            addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByTagKeyword(keyword, page));
            return;
        }

        addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByRegionAndMenuKeyword(
                regionKeyword,
                keyword,
                page
        ));
        addCandidates(candidates, restaurantRepository.searchVisibleRestaurantsByRegionAndTagKeyword(
                regionKeyword,
                keyword,
                page
        ));
    }

    private void addTokenCandidates(
            List<Restaurant> candidates,
            String regionKeyword,
            String keyword,
            PageRequest page
    ) {
        List<String> tokens = SearchRestaurantMatcher.tokenize(keyword);
        if (tokens.size() <= 1) {
            return;
        }

        for (String token : tokens) {
            addKeywordCandidates(candidates, regionKeyword, token, page);
            if (candidates.size() >= INTERNAL_CANDIDATE_FETCH_LIMIT) {
                return;
            }
        }
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

    private String resolveFallbackReason(
            SearchInterpretation interpretation,
            List<SearchRestaurantItemResponse> internalItems,
            boolean userItemsEmpty
    ) {
        if (!userItemsEmpty
                || interpretation.explicitUserQuery()
                || interpretation.restaurantKeyword() == null
                || !isSpecificExternalFallbackKeyword(interpretation.restaurantKeyword())
                || interpretation.genericBrowseQuery()) {
            return null;
        }

        if (internalItems.isEmpty()) {
            return FALLBACK_REASON_NO_INTERNAL_RESULTS;
        }

        if (internalItems.stream().noneMatch(this::isStrongInternalRestaurantMatch)) {
            return FALLBACK_REASON_WEAK_INTERNAL_MATCH;
        }

        if (interpretation.regionKeyword() != null
                && internalItems.size() < FALLBACK_MIN_INTERNAL_RESULTS
                && internalItems.stream().anyMatch(this::isMenuOrTagMatch)
                && internalItems.stream().noneMatch(this::isNameMatch)) {
            return FALLBACK_REASON_LOW_INTERNAL_RESULT_COUNT;
        }

        return null;
    }

    private boolean isStrongInternalRestaurantMatch(SearchRestaurantItemResponse item) {
        String matchedBy = item.matchedBy();
        return SearchRestaurantMatcher.MATCH_NAME_PREFIX.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_NAME_CONTAINS.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_CATEGORY.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_MENU.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_TAG.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_CONVENIENCE.equals(matchedBy)
                || SearchRestaurantMatcher.MATCH_MULTI_TOKEN.equals(matchedBy);
    }

    private boolean isNameMatch(SearchRestaurantItemResponse item) {
        return SearchRestaurantMatcher.MATCH_NAME_PREFIX.equals(item.matchedBy())
                || SearchRestaurantMatcher.MATCH_NAME_CONTAINS.equals(item.matchedBy());
    }

    private boolean isMenuOrTagMatch(SearchRestaurantItemResponse item) {
        return SearchRestaurantMatcher.MATCH_MENU.equals(item.matchedBy())
                || SearchRestaurantMatcher.MATCH_TAG.equals(item.matchedBy());
    }

    private boolean hasNameMatch(List<Restaurant> candidates, String keyword) {
        if (candidates == null || candidates.isEmpty() || keyword == null || keyword.isBlank()) {
            return false;
        }
        return candidates.stream().anyMatch(candidate ->
                startsWithIgnoreCase(candidate.getName(), keyword)
                        || SearchRestaurantMatcher.containsIgnoreCase(candidate.getName(), keyword));
    }

    private Comparator<SearchRestaurantItemResponse> internalResultComparator(Map<Long, Integer> rankingOrder) {
        if (!rankingOrder.isEmpty()) {
            return Comparator
                    .comparingInt((SearchRestaurantItemResponse item) -> SearchRestaurantMatcher.matchPriority(item.matchedBy()))
                    .thenComparingInt(item -> rankingOrder.getOrDefault(item.restaurantId(), Integer.MAX_VALUE))
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
        String category = resolveRankingCategory(interpretation.restaurantKeyword(), internalCandidates);
        if (regionName == null && category == null && interpretation.restaurantKeyword() != null) {
            return Map.of();
        }
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

    private String resolveRankingCategory(String restaurantKeyword, List<Restaurant> internalCandidates) {
        if (restaurantKeyword == null || restaurantKeyword.isBlank()) {
            return null;
        }
        List<String> tokens = SearchRestaurantMatcher.tokenize(restaurantKeyword);
        if (tokens.isEmpty()) {
            return null;
        }
        String categoryCandidate = tokens.get(tokens.size() - 1);
        if (internalCandidates == null || internalCandidates.isEmpty()) {
            return null;
        }

        boolean matchedCategory = internalCandidates.stream().anyMatch(restaurant ->
                SearchRestaurantMatcher.containsIgnoreCase(restaurant.getCategoryName(), categoryCandidate)
                        || SearchRestaurantMatcher.containsIgnoreCase(restaurant.getPrimaryCategoryName(), categoryCandidate));
        return matchedCategory ? categoryCandidate : null;
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
            if (!isLikelyFoodPlace(candidate)) {
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

            Optional<Restaurant> internalRestaurant = findVisibleInternalRestaurant(candidate.placeId());
            if (internalRestaurant.isPresent()) {
                Restaurant restaurant = internalRestaurant.get();
                addInternalDedupKey(dedupKeys, restaurant.getName(), restaurant.getAddress());
                addInternalDedupKey(dedupKeys, restaurant.getName(), restaurant.getRoadAddress());
                items.add(SearchResultMapper.toInternalRestaurantItem(
                        restaurant,
                        SearchRestaurantMatcher.MATCH_EXTERNAL_FALLBACK,
                        SOURCE_INTERNAL
                ));
                if (items.size() >= EXTERNAL_FALLBACK_LIMIT) {
                    break;
                }
                continue;
            }

            Optional<Restaurant> persistedRestaurant = persistExternalSearchCandidate(
                    candidate,
                    interpretation.regionKeyword()
            );
            if (persistedRestaurant.isEmpty()) {
                continue;
            }

            Restaurant restaurant = persistedRestaurant.get();
            items.add(SearchResultMapper.toInternalRestaurantItem(
                    restaurant,
                    SearchRestaurantMatcher.MATCH_EXTERNAL_FALLBACK,
                    SOURCE_INTERNAL
            ));

            if (items.size() >= EXTERNAL_FALLBACK_LIMIT) {
                break;
            }
        }

        return items;
    }

    private boolean isSpecificExternalFallbackKeyword(String keyword) {
        List<String> tokens = SearchRestaurantMatcher.tokenize(keyword);
        if (tokens.isEmpty()) {
            return false;
        }
        if (tokens.stream().allMatch(BROAD_EXTERNAL_FALLBACK_KEYWORDS::contains)) {
            return false;
        }
        String compact = String.join("", tokens);
        return !BROAD_EXTERNAL_FALLBACK_KEYWORDS.contains(compact);
    }

    private Optional<Restaurant> persistExternalSearchCandidate(
            PcmapRestaurantCandidate candidate,
            String regionKeyword
    ) {
        if (candidate.placeId() == null || candidate.placeId().isBlank()) {
            return Optional.empty();
        }

        Optional<Restaurant> existingByPlaceId = findVisibleInternalRestaurant(candidate.placeId());
        if (existingByPlaceId.isPresent()) {
            return existingByPlaceId;
        }

        String address = resolveExternalAddress(candidate);
        if (candidate.name() == null || candidate.name().isBlank() || address == null || address.isBlank()) {
            return Optional.empty();
        }

        Optional<Restaurant> existingByNameAndAddress = restaurantRepository.findByNameAndAddress(
                candidate.name(),
                address
        );
        if (existingByNameAndAddress.isPresent()) {
            Restaurant restaurant = existingByNameAndAddress.get();
            return isVisible(restaurant) ? Optional.of(restaurant) : Optional.empty();
        }

        RegionSchema regionSchema = RestaurantRegionResolver.resolve(
                regionKeyword,
                address,
                candidate.address(),
                candidate.roadAddress(),
                candidate.fullAddress()
        );
        String resolvedRegionName = firstNonBlank(
                regionSchema.regionName(),
                regionKeyword,
                resolveFallbackRegionName(address)
        );
        if (resolvedRegionName == null) {
            return Optional.empty();
        }
        if (regionKeyword != null && !matchesResolvedRegion(regionSchema, candidate, regionKeyword)) {
            return Optional.empty();
        }

        Restaurant restaurant = Restaurant.builder()
                .name(candidate.name())
                .address(address)
                .roadAddress(candidate.roadAddress())
                .categoryName(candidate.categoryName())
                .primaryCategoryName(RestaurantCategoryResolver.resolvePrimaryCategory(candidate.categoryName()))
                .regionName(resolvedRegionName)
                .regionCityName(regionSchema.regionCityName())
                .regionDistrictName(regionSchema.regionDistrictName())
                .regionCountyName(regionSchema.regionCountyName())
                .regionTownName(regionSchema.regionTownName())
                .regionFilterNames(resolveExternalRegionFilterNames(regionSchema, resolvedRegionName, candidate))
                .lat(parseCoordinate(candidate.y()))
                .lng(parseCoordinate(candidate.x()))
                .imageUrl(candidate.imageUrl())
                .pcmapPlaceId(candidate.placeId())
                .build();

        try {
            return Optional.of(restaurantRepository.save(restaurant));
        } catch (DataIntegrityViolationException exception) {
            return findVisibleInternalRestaurant(candidate.placeId());
        }
    }

    private boolean matchesResolvedRegion(
            RegionSchema regionSchema,
            PcmapRestaurantCandidate candidate,
            String regionKeyword
    ) {
        return SearchRestaurantMatcher.containsIgnoreCase(regionSchema.regionName(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(regionSchema.regionCityName(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(regionSchema.regionDistrictName(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(regionSchema.regionCountyName(), regionKeyword)
                || SearchRestaurantMatcher.containsIgnoreCase(regionSchema.regionTownName(), regionKeyword)
                || regionSchema.regionFilterNames().stream()
                .anyMatch(filterName -> SearchRestaurantMatcher.containsIgnoreCase(filterName, regionKeyword))
                || matchesExternalRegion(candidate, regionKeyword);
    }

    private List<String> resolveExternalRegionFilterNames(
            RegionSchema regionSchema,
            String resolvedRegionName,
            PcmapRestaurantCandidate candidate
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (regionSchema.regionFilterNames() != null) {
            regionSchema.regionFilterNames().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(values::add);
        }
        addIfPresent(values, resolvedRegionName);
        addIfPresent(values, candidate.address());
        addIfPresent(values, candidate.roadAddress());
        addIfPresent(values, candidate.fullAddress());
        return new ArrayList<>(values);
    }

    private String resolveFallbackRegionName(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        List<String> tokens = SearchRestaurantMatcher.tokenize(address);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .limit(2)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private Optional<Restaurant> findVisibleInternalRestaurant(String pcmapPlaceId) {
        if (pcmapPlaceId == null || pcmapPlaceId.isBlank()) {
            return Optional.empty();
        }
        return restaurantRepository.findByPcmapPlaceId(pcmapPlaceId)
                .filter(this::isVisible);
    }

    private boolean isVisible(Restaurant restaurant) {
        return restaurant != null
                && !Boolean.TRUE.equals(restaurant.getIsDeleted())
                && !Boolean.TRUE.equals(restaurant.getIsHidden());
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

    private boolean isLikelyFoodPlace(PcmapRestaurantCandidate candidate) {
        String category = candidate.categoryName();
        if (category == null || category.isBlank()) {
            return true;
        }

        List<String> blockedSignals = List.of(
                "병원", "약국", "숙박", "호텔", "모텔", "펜션", "학교", "대학교", "학원",
                "마트", "편의점", "쇼핑", "미용", "헤어", "네일", "부동산", "은행",
                "주차장", "공원", "관광", "스포츠", "운동", "수리", "세탁"
        );
        return blockedSignals.stream()
                .noneMatch(signal -> SearchRestaurantMatcher.containsIgnoreCase(category, signal));
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

    private boolean startsWithIgnoreCase(String source, String prefix) {
        if (source == null || prefix == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private record RestaurantSearchResult(
            List<SearchRestaurantItemResponse> items,
            boolean fallbackUsed,
            boolean fallbackAttempted,
            String fallbackReason,
            int fallbackResultCount
    ) {
    }
}
