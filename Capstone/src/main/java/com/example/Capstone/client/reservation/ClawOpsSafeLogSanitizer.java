package com.example.Capstone.client.reservation;

import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public final class ClawOpsSafeLogSanitizer {

    private static final int MAX_BODY_SUMMARY_LENGTH = 600;
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?i)(api[-_ ]?key|secret|signing[-_ ]?key|token|authorization)\\s*[:=]\\s*[^\\s,}]+"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?\\d[\\d\\-\\s().]{7,}\\d)(?!\\d)"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ClawOpsSafeLogSanitizer() {
    }

    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        if (normalized.length() <= 4) {
            return "****";
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }

    public static String sanitizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        String trimmedBody = responseBody.trim();
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(trimmedBody);
            JsonNode sanitized = sanitizeJsonNode(jsonNode, null);
            return limit(OBJECT_MAPPER.writeValueAsString(sanitized));
        } catch (JsonProcessingException ignored) {
            return limit(maskSensitiveText(trimmedBody.replaceAll("\\s+", " ")));
        }
    }

    public static String maskSensitiveText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = BEARER_PATTERN.matcher(value).replaceAll("Bearer [REDACTED]");
        masked = JWT_PATTERN.matcher(masked).replaceAll("[TOKEN_REDACTED]");
        masked = KEY_VALUE_SECRET_PATTERN.matcher(masked).replaceAll("$1=[REDACTED]");
        return PHONE_PATTERN.matcher(masked).replaceAll("[PHONE_REDACTED]");
    }

    private static JsonNode sanitizeJsonNode(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (fieldName != null && isSensitiveField(fieldName)) {
            return TextNode.valueOf("[REDACTED]");
        }
        if (node.isTextual()) {
            return TextNode.valueOf(maskSensitiveText(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode copy = OBJECT_MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                copy.set(field.getKey(), sanitizeJsonNode(field.getValue(), field.getKey()));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = OBJECT_MAPPER.createArrayNode();
            for (JsonNode item : node) {
                copy.add(sanitizeJsonNode(item, fieldName));
            }
            return copy;
        }
        return node;
    }

    private static boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.toLowerCase();
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("api-key")
                || normalized.contains("signing")
                || normalized.contains("phone")
                || normalized.equals("to")
                || normalized.equals("from")
                || normalized.endsWith("_number")
                || normalized.endsWith("number");
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_BODY_SUMMARY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_BODY_SUMMARY_LENGTH) + "...[truncated]";
    }
}
