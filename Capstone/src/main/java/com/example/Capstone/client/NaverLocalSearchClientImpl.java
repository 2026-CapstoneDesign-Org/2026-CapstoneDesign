package com.example.Capstone.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.Capstone.client.NaverLocalSearchClient.NaverLocalRestaurantCandidate;
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
            List<NaverLocalRestaurantCandidate> candidates = searchLocal(buildQuery(restaurantName, address));
            return candidates.stream()
                    .map(candidate -> new ScoredCandidate(candidate, score(candidate, restaurantName, address)))
                    .filter(candidate -> candidate.score() >= 5)
                    .max(Comparator.comparingInt(ScoredCandidate::score))
                    .map(ScoredCandidate::candidate);
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

    private String buildQuery(String restaurantName, String address) {
        if (isBlank(address)) {
            return restaurantName.trim();
        }

        return address.trim() + " " + restaurantName.trim();
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
        String expectedAddress = normalizeForMatch(address);
        String actualAddress = normalizeForMatch(candidate.roadAddress() + " " + candidate.address());

        if (!expectedName.isBlank() && actualName.equals(expectedName)) {
            score += 6;
        } else if (!expectedName.isBlank()
                && (actualName.contains(expectedName) || expectedName.contains(actualName))) {
            score += 4;
        }

        if (!expectedAddress.isBlank()
                && (actualAddress.contains(expectedAddress) || expectedAddress.contains(actualAddress))) {
            score += 3;
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
                .replaceAll("\\s+", "")
                .toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoredCandidate(NaverLocalRestaurantCandidate candidate, int score) {
    }
}
