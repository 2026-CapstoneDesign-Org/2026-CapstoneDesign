package com.example.Capstone.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NaverLocalSearchClientImpl implements NaverLocalSearchClient {

    private static final String LOCAL_SEARCH_URL = "https://openapi.naver.com/v1/search/local.json";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;

    public NaverLocalSearchClientImpl(
            ObjectMapper objectMapper,
            @Value("${search.naver-local.enabled:true}") boolean enabled,
            @Value("${search.naver-local.client-id:${NAVER_SEARCH_CLIENT_ID:${NAVER_CLIENT_ID:}}}") String clientId,
            @Value("${search.naver-local.client-secret:${NAVER_SEARCH_CLIENT_SECRET:${NAVER_CLIENT_SECRET:}}}") String clientSecret
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.enabled = enabled;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    @Override
    public Optional<NaverLocalRestaurantCandidate> findBestRestaurantMatch(String restaurantName, String address) {
        if (!canCall() || isBlank(restaurantName)) {
            return Optional.empty();
        }

        try {
            List<NaverLocalRestaurantCandidate> candidates = new ArrayList<>();
            Set<String> dedupKeys = new LinkedHashSet<>();
            for (String query : buildQueries(restaurantName, address)) {
                for (NaverLocalRestaurantCandidate candidate : searchLocal(query)) {
                    String dedupKey = normalizeForMatch(candidate.title()) + "|"
                            + normalizeForMatch(candidate.roadAddress() + " " + candidate.address());
                    if (dedupKeys.add(dedupKey)) {
                        candidates.add(candidate);
                    }
                }
            }

            Optional<ScoredCandidate> bestCandidate = candidates.stream()
                    .map(candidate -> new ScoredCandidate(candidate, score(candidate, restaurantName, address)))
                    .max(Comparator.comparingInt(ScoredCandidate::score));

            if (bestCandidate.isEmpty()) {
                log.debug("naver local search returned no candidates for restaurantName={}", restaurantName);
                return Optional.empty();
            }
            if (bestCandidate.get().score() < 5) {
                log.debug(
                        "naver local search best candidate score too low for restaurantName={}, bestTitle={}, score={}",
                        restaurantName,
                        bestCandidate.get().candidate().title(),
                        bestCandidate.get().score()
                );
                return Optional.empty();
            }

            return bestCandidate.map(ScoredCandidate::candidate);
        } catch (Exception exception) {
            log.warn("naver local search failed for restaurantName={}", restaurantName, exception);
            return Optional.empty();
        }
    }

    private boolean canCall() {
        return enabled
                && !isBlank(clientId)
                && !isBlank(clientSecret)
                && !clientId.startsWith("dummy-")
                && !clientSecret.startsWith("dummy-");
    }

    private List<String> buildQueries(String restaurantName, String address) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String normalizedName = normalizeText(restaurantName);
        String normalizedAddress = normalizeText(address);

        if (normalizedAddress != null) {
            queries.add(normalizedAddress + " " + normalizedName);

            List<String> addressTokens = tokenizeAddress(normalizedAddress);
            if (!addressTokens.isEmpty()) {
                StringBuilder regionQuery = new StringBuilder();
                for (int index = 0; index < Math.min(3, addressTokens.size()); index += 1) {
                    if (regionQuery.length() > 0) {
                        regionQuery.append(' ');
                    }
                    regionQuery.append(addressTokens.get(index));
                }
                regionQuery.append(' ').append(normalizedName);
                queries.add(regionQuery.toString());
            }
        }

        queries.add(normalizedName);
        return new ArrayList<>(queries);
    }

    private List<NaverLocalRestaurantCandidate> searchLocal(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = LOCAL_SEARCH_URL
                + "?query=" + encodedQuery
                + "&display=5"
                + "&start=1"
                + "&sort=random";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("naver local search failed with status " + response.statusCode());
        }

        JsonNode items = objectMapper.readTree(response.body()).path("items");
        if (!items.isArray()) {
            return List.of();
        }

        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(this::toCandidate)
                .filter(candidate -> !isBlank(candidate.title()))
                .toList();
    }

    private NaverLocalRestaurantCandidate toCandidate(JsonNode item) {
        return new NaverLocalRestaurantCandidate(
                stripHtml(item.path("title").asText(null)),
                normalizeText(item.path("category").asText(null)),
                normalizeText(item.path("telephone").asText(null)),
                normalizeText(item.path("address").asText(null)),
                normalizeText(item.path("roadAddress").asText(null)),
                normalizeText(item.path("mapx").asText(null)),
                normalizeText(item.path("mapy").asText(null))
        );
    }

    private int score(NaverLocalRestaurantCandidate candidate, String restaurantName, String address) {
        int score = 0;
        String expectedName = normalizeForMatch(restaurantName);
        String actualName = normalizeForMatch(candidate.title());

        if (!expectedName.isBlank() && actualName.equals(expectedName)) {
            score += 6;
        } else if (!expectedName.isBlank()
                && (actualName.contains(expectedName) || expectedName.contains(actualName))) {
            score += 4;
        } else {
            score += sharedTokenCount(tokenizeName(restaurantName), tokenizeName(candidate.title()));
        }

        String expectedAddress = normalizeForMatch(address);
        String actualAddress = normalizeForMatch(candidate.roadAddress() + " " + candidate.address());
        if (!expectedAddress.isBlank()
                && (actualAddress.contains(expectedAddress) || expectedAddress.contains(actualAddress))) {
            score += 3;
        } else {
            score += Math.min(3, sharedTokenCount(tokenizeAddress(address), tokenizeAddress(
                    candidate.roadAddress() + " " + candidate.address()
            )));
        }

        return score;
    }

    private String stripHtml(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("<[^>]+>", "").trim();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        String stripped = stripHtml(value);
        if (stripped == null) {
            return "";
        }
        return stripped
                .replaceAll("[^0-9A-Za-z가-힣]", "")
                .toLowerCase();
    }

    private List<String> tokenizeName(String value) {
        String normalized = stripHtml(value);
        if (normalized == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("[^0-9A-Za-z가-힣]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .map(String::toLowerCase)
                .toList();
    }

    private List<String> tokenizeAddress(String value) {
        String normalized = stripHtml(value);
        if (normalized == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("[^0-9A-Za-z가-힣]+"))
                .map(this::normalizeAddressToken)
                .filter(token -> token.length() >= 2 || token.matches("\\d+"))
                .distinct()
                .toList();
    }

    private String normalizeAddressToken(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase();
        if (normalized.endsWith("특별시")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("광역시")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("특별자치시")) {
            return normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private int sharedTokenCount(List<String> expectedTokens, List<String> actualTokens) {
        int count = 0;
        for (String expectedToken : expectedTokens) {
            for (String actualToken : actualTokens) {
                if (actualToken.equals(expectedToken)
                        || actualToken.contains(expectedToken)
                        || expectedToken.contains(actualToken)) {
                    count += 1;
                    break;
                }
            }
        }
        return count;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoredCandidate(NaverLocalRestaurantCandidate candidate, int score) {
    }
}
