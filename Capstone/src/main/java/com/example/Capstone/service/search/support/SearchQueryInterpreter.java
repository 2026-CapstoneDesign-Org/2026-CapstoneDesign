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
            "추천"
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
        String regionKeyword = detectRegionKeyword(tokens);
        String restaurantKeyword = deriveRestaurantKeyword(tokens, regionKeyword);

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

    private boolean isGenericOnlyPhrase(String phrase) {
        List<String> phraseTokens = tokenize(phrase);
        return !phraseTokens.isEmpty() && phraseTokens.stream().allMatch(GENERIC_BROWSE_TERMS::contains);
    }

    private String deriveRestaurantKeyword(List<String> tokens, String regionKeyword) {
        List<String> remainingTokens = new ArrayList<>(tokens);
        if (regionKeyword != null) {
            remainingTokens.removeAll(tokenize(regionKeyword));
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
}
