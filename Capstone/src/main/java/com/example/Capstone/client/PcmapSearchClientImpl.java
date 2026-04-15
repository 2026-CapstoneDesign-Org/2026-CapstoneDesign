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
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.Capstone.client.PcmapSearchClient.PcmapRestaurantCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PcmapSearchClientImpl implements PcmapSearchClient {

    private static final String APOLLO_MARKER = "window.__APOLLO_STATE__";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String centerX;
    private final String centerY;
    private final int display;
    private final String cookie;

    public PcmapSearchClientImpl(
            ObjectMapper objectMapper,
            @Value("${search.pcmap.enabled:true}") boolean enabled,
            @Value("${search.pcmap.center-x:127.1775537}") String centerX,
            @Value("${search.pcmap.center-y:37.2410864}") String centerY,
            @Value("${search.pcmap.display:10}") int display,
            @Value("${NAVER_COOKIE:}") String cookie
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.enabled = enabled;
        this.centerX = centerX;
        this.centerY = centerY;
        this.display = display;
        this.cookie = cookie == null ? "" : cookie.trim();
    }

    @Override
    public List<PcmapRestaurantCandidate> searchRestaurants(String keyword, int limit) {
        if (!enabled || keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            String html = fetchSearchPage(keyword.trim(), Math.max(limit, display));
            JsonNode apolloState = extractApolloState(html);
            JsonNode items = resolveSearchItems(apolloState);
            if (items == null || !items.isArray()) {
                return List.of();
            }

            List<PcmapRestaurantCandidate> candidates = new ArrayList<>();
            for (JsonNode item : items) {
                JsonNode resolved = resolveApolloValue(apolloState, item);
                PcmapRestaurantCandidate candidate = normalizeCandidate(resolved);
                if (candidate != null && candidates.stream().noneMatch(existing -> existing.placeId().equals(candidate.placeId()))) {
                    candidates.add(candidate);
                }
                if (candidates.size() >= limit) {
                    break;
                }
            }
            return candidates;
        } catch (Exception exception) {
            log.warn("pcmap fallback search failed for keyword={}", keyword, exception);
            return List.of();
        }
    }

    private String fetchSearchPage(String keyword, int requestedDisplay) throws IOException, InterruptedException {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = "https://pcmap.place.naver.com/place/list"
                + "?query=" + encodedKeyword
                + "&x=" + centerX
                + "&y=" + centerY
                + "&clientX=" + centerX
                + "&clientY=" + centerY
                + "&from=map"
                + "&display=" + requestedDisplay
                + "&locale=ko"
                + "&svcName=map_pcv5"
                + "&noredirect=1";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Origin", "https://pcmap.place.naver.com")
                .header("Referer", "https://map.naver.com/")
                .header("User-Agent", "Mozilla/5.0");

        if (!cookie.isBlank()) {
            requestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("pcmap request failed with status " + response.statusCode());
        }
        return response.body();
    }

    private JsonNode extractApolloState(String html) throws IOException {
        int markerIndex = html.indexOf(APOLLO_MARKER);
        if (markerIndex < 0) {
            throw new IllegalStateException("pcmap apollo state marker not found");
        }

        int objectStart = html.indexOf('{', markerIndex);
        if (objectStart < 0) {
            throw new IllegalStateException("pcmap apollo state start not found");
        }

        String objectText = extractBalancedObject(html, objectStart);
        if (objectText.isBlank()) {
            throw new IllegalStateException("pcmap apollo state body not found");
        }

        return objectMapper.readTree(objectText);
    }

    private String extractBalancedObject(String text, int startIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = startIndex; index < text.length(); index += 1) {
            char character = text.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
                continue;
            }

            if (character == '{') {
                depth += 1;
                continue;
            }

            if (character == '}') {
                depth -= 1;
                if (depth == 0) {
                    return text.substring(startIndex, index + 1);
                }
            }
        }

        return "";
    }

    private JsonNode resolveSearchItems(JsonNode apolloState) {
        JsonNode rootQuery = apolloState.path("ROOT_QUERY");
        Iterator<String> fieldNames = rootQuery.fieldNames();

        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!fieldName.startsWith("places(")) {
                continue;
            }

            JsonNode rawResult = resolveApolloValue(apolloState, rootQuery.get(fieldName));
            JsonNode items = firstNonNull(
                    rawResult.path("items"),
                    rawResult.path("place").path("list"),
                    rawResult.path("list"),
                    rawResult.path("places").path("items")
            );
            if (items != null && items.isArray()) {
                return items;
            }
        }

        return null;
    }

    private JsonNode resolveApolloValue(JsonNode apolloState, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return value;
        }

        if (value.has("__ref")) {
            return apolloState.get(value.get("__ref").asText());
        }

        return value;
    }

    private JsonNode firstNonNull(JsonNode... values) {
        for (JsonNode value : values) {
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private PcmapRestaurantCandidate normalizeCandidate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String placeId = text(node, "placeId", "id");
        String name = text(node, "name");
        if (placeId == null || name == null) {
            return null;
        }

        return new PcmapRestaurantCandidate(
                placeId,
                name,
                text(node, "category", "categoryName"),
                text(node, "address", "commonAddress", "fullAddress"),
                text(node, "roadAddress"),
                text(node, "fullAddress"),
                text(node, "imageUrl"),
                text(node, "x", "longitude"),
                text(node, "y", "latitude")
        );
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                String value = field.asText().trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
