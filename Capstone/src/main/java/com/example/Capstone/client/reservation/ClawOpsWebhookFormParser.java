package com.example.Capstone.client.reservation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClawOpsWebhookFormParser {

    private ClawOpsWebhookFormParser() {
    }

    static Map<String, String> parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Map.of();
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        String[] pairs = rawPayload.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }

            int separatorIndex = pair.indexOf('=');
            String key = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            String value = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            String decodedKey = decode(key);
            if (decodedKey.isBlank()) {
                continue;
            }
            parameters.put(decodedKey, decode(value));
        }

        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
