package com.example.Capstone.service.search.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;

import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.repository.RestaurantRepository;

public final class SearchQueryInterpreter {

    private static final Set<String> GENERIC_BROWSE_TERMS = Set.of(
            "맛집",
            "식당",
            "밥집",
            "추천",
            "근처",
            "주변"
    );

    private static final Set<String> NON_REGION_TERMS = Set.of(
            "가능",
            "가능한",
            "주차",
            "포장",
            "배달",
            "예약",
            "단체",
            "카페",
            "고기",
            "냉면",
            "김밥",
            "돈까스",
            "혼밥"
    );

    private final RestaurantRepository restaurantRepository;

    public SearchQueryInterpreter(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public SearchInterpretation interpret(String rawQuery) {
        boolean explicitRegionQuery = rawQuery.startsWith("@@");
        boolean explicitUserQuery = !explicitRegionQuery && rawQuery.startsWith("@");
        String normalizedQuery = normalizeQuery(
                explicitRegionQuery ? rawQuery.substring(2)
                        : explicitUserQuery ? rawQuery.substring(1)
                        : rawQuery
        );

        if (explicitUserQuery) {
            return new SearchInterpretation(
                    rawQuery,
                    normalizedQuery,
                    true,
                    false,
                    normalizedQuery,
                    null,
                    null,
                    false,
                    false
            );
        }

        List<String> tokens = tokenize(normalizedQuery);
        RegionDetection aliasRegion = detectAliasRegion(tokens);
        String regionKeyword = aliasRegion == null ? detectRegionKeyword(tokens) : aliasRegion.keyword();
        String restaurantKeyword = deriveRestaurantKeyword(
                tokens,
                aliasRegion == null ? tokenize(regionKeyword) : aliasRegion.tokensToRemove()
        );

        return new SearchInterpretation(
                rawQuery,
                normalizedQuery,
                false,
                explicitRegionQuery,
                normalizedQuery,
                regionKeyword,
                restaurantKeyword,
                regionKeyword != null && restaurantKeyword == null,
                false
        );
    }

    public static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    private RegionDetection detectAliasRegion(List<String> tokens) {
        if (tokens.contains("명지대")) {
            List<String> aliasTokens = new ArrayList<>();
            aliasTokens.add("명지대");
            if (tokens.contains("근처")) {
                aliasTokens.add("근처");
            }
            return new RegionDetection("역북동", aliasTokens);
        }
        return null;
    }

    private String detectRegionKeyword(List<String> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }

        for (int size = tokens.size(); size >= 1; size -= 1) {
            for (int start = 0; start <= tokens.size() - size; start += 1) {
                String phrase = String.join(" ", tokens.subList(start, start + size));
                if (isGenericOnlyPhrase(phrase)) {
                    continue;
                }
                if (!looksLikeRegionPhrase(phrase)) {
                    continue;
                }

                List<Restaurant> matches = restaurantRepository.searchVisibleRestaurantsByRegionSignal(
                        phrase,
                        PageRequest.of(0, 1)
                );
                if (!matches.isEmpty()) {
                    return phrase;
                }
            }
        }

        return null;
    }

    private boolean looksLikeRegionPhrase(String phrase) {
        List<String> phraseTokens = tokenize(phrase);
        if (phraseTokens.isEmpty()) {
            return false;
        }
        if (phraseTokens.size() == 1 && NON_REGION_TERMS.contains(phraseTokens.get(0))) {
            return false;
        }
        String compact = String.join("", phraseTokens);
        if (Set.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주").contains(compact)) {
            return true;
        }
        if (compact.endsWith("시")
                || compact.endsWith("군")
                || compact.endsWith("구")
                || compact.endsWith("동")
                || compact.endsWith("읍")
                || compact.endsWith("면")
                || compact.endsWith("가")
                || compact.endsWith("리")) {
            return true;
        }

        return true;
    }

    private boolean isGenericOnlyPhrase(String phrase) {
        List<String> phraseTokens = tokenize(phrase);
        return !phraseTokens.isEmpty() && phraseTokens.stream().allMatch(GENERIC_BROWSE_TERMS::contains);
    }

    private String deriveRestaurantKeyword(List<String> tokens, List<String> regionTokens) {
        List<String> remainingTokens = new ArrayList<>(tokens);
        if (regionTokens != null && !regionTokens.isEmpty()) {
            remainingTokens.removeAll(regionTokens);
        }

        List<String> filtered = remainingTokens.stream()
                .filter(token -> !GENERIC_BROWSE_TERMS.contains(token))
                .toList();

        if (filtered.isEmpty()) {
            return null;
        }

        return String.join(" ", filtered);
    }

    private List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return Arrays.stream(query.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private record RegionDetection(String keyword, List<String> tokensToRemove) {
    }
}
