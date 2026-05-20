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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.Capstone.client.PcmapPlaceDetailClient.PcmapMenuItem;
import com.example.Capstone.client.PcmapPlaceDetailClient.PcmapRestaurantDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PcmapPlaceDetailClientImpl implements PcmapPlaceDetailClient {

    private static final String APOLLO_MARKER = "window.__APOLLO_STATE__";
    private static final int RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MILLIS = 1500L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final long minIntervalMillis;
    private final String cookie;
    private final Object rateLimitLock = new Object();
    private volatile long nextAllowedRequestAtMillis = 0L;

    public PcmapPlaceDetailClientImpl(
            ObjectMapper objectMapper,
            @Value("${search.pcmap.enabled:true}") boolean enabled,
            @Value("${search.pcmap.min-interval-ms:3000}") long minIntervalMillis,
            @Value("${NAVER_COOKIE:}") String cookie
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.enabled = enabled;
        this.minIntervalMillis = Math.max(0L, minIntervalMillis);
        this.cookie = cookie == null ? "" : cookie.trim();
    }

    @Override
    public Optional<PcmapRestaurantDetail> fetchRestaurantDetail(String placeId) {
        String normalizedPlaceId = normalizeText(placeId);
        if (!enabled || normalizedPlaceId == null) {
            return Optional.empty();
        }

        try {
            String html = fetchMenuPage(normalizedPlaceId);
            JsonNode apolloState = extractApolloState(html);
            JsonNode placeNode = findPlaceNode(apolloState, normalizedPlaceId);
            if (placeNode == null) {
                return Optional.empty();
            }
            return Optional.of(toDetail(apolloState, placeNode, normalizedPlaceId));
        } catch (Exception exception) {
            log.warn("pcmap place detail fetch failed for placeId={}", normalizedPlaceId, exception);
            return Optional.empty();
        }
    }

    private String fetchMenuPage(String placeId) throws IOException, InterruptedException {
        String encodedPlaceId = URLEncoder.encode(placeId, StandardCharsets.UTF_8);
        String url = "https://pcmap.place.naver.com/restaurant/" + encodedPlaceId + "/menu/list"
                + "?fromPanelNum=1"
                + "&additionalHeight=76"
                + "&timestamp=" + System.currentTimeMillis()
                + "&locale=ko"
                + "&svcName=map_pcv5";

        Exception lastException = null;
        for (int attempt = 1; attempt <= RETRY_COUNT; attempt += 1) {
            try {
                waitForRequestSlot();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Origin", "https://pcmap.place.naver.com")
                        .header("Referer", "https://pcmap.place.naver.com/restaurant/" + encodedPlaceId + "/home")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

                if (!cookie.isBlank()) {
                    requestBuilder.header("Cookie", cookie);
                }

                HttpResponse<String> response = httpClient.send(
                        requestBuilder.GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                throw new IllegalStateException("pcmap detail request failed with status " + response.statusCode());
            } catch (Exception exception) {
                lastException = exception;
                if (attempt >= RETRY_COUNT) {
                    break;
                }
                Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            }
        }

        if (lastException instanceof IOException ioException) {
            throw ioException;
        }
        if (lastException instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("pcmap detail request failed");
    }

    private void waitForRequestSlot() throws InterruptedException {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long waitMillis = nextAllowedRequestAtMillis - now;
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }
            nextAllowedRequestAtMillis = System.currentTimeMillis() + minIntervalMillis;
        }
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

    private JsonNode findPlaceNode(JsonNode apolloState, String placeId) {
        Iterator<String> fieldNames = apolloState.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode resolved = resolveApolloValue(apolloState, apolloState.get(fieldName));
            if (resolved == null || !resolved.isObject()) {
                continue;
            }
            String nodePlaceId = text(resolved, "placeId", "id");
            if (!placeId.equals(nodePlaceId)) {
                continue;
            }
            if (text(resolved, "name") != null
                    && firstText(resolved, "address", "roadAddress", "fullAddress", "commonAddress") != null) {
                return resolved;
            }
        }
        return null;
    }

    private PcmapRestaurantDetail toDetail(JsonNode apolloState, JsonNode placeNode, String requestedPlaceId) {
        JsonNode coordinate = resolveApolloValue(apolloState, placeNode.get("coordinate"));
        JsonNode missingInfo = resolveApolloValue(apolloState, placeNode.get("missingInfo"));
        String businessType = text(missingInfo, "businessType");

        return new PcmapRestaurantDetail(
                firstText(placeNode, "placeId", "id", requestedPlaceId),
                text(placeNode, "name"),
                firstText(placeNode, "category", "categoryName"),
                firstText(placeNode, "address", "commonAddress", "fullAddress"),
                text(placeNode, "roadAddress"),
                text(placeNode, "imageUrl"),
                firstText(placeNode, "x", "longitude", text(coordinate, "x", "longitude")),
                firstText(placeNode, "y", "latitude", text(coordinate, "y", "latitude")),
                firstText(placeNode, "phone", "virtualPhone"),
                businessType,
                resolveStringList(apolloState, placeNode.get("conveniences")),
                resolveBusinessHoursRaw(apolloState, placeNode),
                resolveMenus(apolloState, placeNode, requestedPlaceId)
        );
    }

    private List<String> resolveStringList(JsonNode apolloState, JsonNode node) {
        JsonNode resolved = resolveApolloValue(apolloState, node);
        if (resolved == null || !resolved.isArray()) {
            return List.of();
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : resolved) {
            String value = normalizeText(item.isObject() ? firstText(item, "name", "text", "value") : item.asText());
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private String resolveBusinessHoursRaw(JsonNode apolloState, JsonNode placeNode) {
        JsonNode value = firstNode(
                resolveApolloValue(apolloState, placeNode.get("newBusinessHours")),
                resolveApolloValue(apolloState, placeNode.get("businessHours")),
                resolveApolloValue(apolloState, placeNode.get("openingHours"))
        );
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<PcmapMenuItem> resolveMenus(JsonNode apolloState, JsonNode placeNode, String placeId) {
        List<PcmapMenuItem> menus = new ArrayList<>();
        JsonNode directMenus = resolveApolloValue(apolloState, placeNode.get("menus"));
        if (directMenus != null && directMenus.isArray()) {
            int index = 0;
            for (JsonNode menu : directMenus) {
                addMenu(menus, menu, index);
                index += 1;
            }
        }

        if (!menus.isEmpty()) {
            return menus;
        }

        Iterator<String> fieldNames = apolloState.fieldNames();
        int index = 0;
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode node = resolveApolloValue(apolloState, apolloState.get(fieldName));
            if (node == null || !node.isObject()) {
                continue;
            }
            String typeName = text(node, "__typename");
            String menuName = firstText(node, "name", "title", "menu", "menuName");
            if (menuName == null) {
                continue;
            }
            boolean samePlace = placeId.equals(text(node, "placeId"));
            boolean looksLikeMenu = typeName != null && typeName.toLowerCase().contains("menu");
            boolean keyedByPlace = fieldName.contains(":" + placeId + "_") || fieldName.contains(":" + placeId + ":");
            if (samePlace || looksLikeMenu || keyedByPlace) {
                addMenu(menus, node, index);
                index += 1;
            }
        }
        return menus;
    }

    private void addMenu(List<PcmapMenuItem> menus, JsonNode node, int fallbackIndex) {
        String name = firstText(node, "name", "title", "menu", "menuName");
        if (name == null) {
            return;
        }
        menus.add(new PcmapMenuItem(
                node.has("index") && node.get("index").canConvertToInt() ? node.get("index").asInt() : fallbackIndex,
                name,
                firstText(node, "price", "priceText", "salePrice"),
                firstText(node, "description", "summary", "desc"),
                text(node, "__typename")
        ));
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

    private JsonNode firstNode(JsonNode... values) {
        for (JsonNode value : values) {
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String fieldName1, String fieldName2, String fallbackValue) {
        String value = firstText(node, fieldName1, fieldName2);
        return value == null ? normalizeText(fallbackValue) : value;
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                String value = normalizeText(field.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
