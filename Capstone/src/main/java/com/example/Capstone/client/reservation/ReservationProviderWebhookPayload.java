package com.example.Capstone.client.reservation;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public record ReservationProviderWebhookPayload(
        String provider,
        Map<String, String> headers,
        String rawPayload
) {
    public ReservationProviderWebhookPayload {
        headers = headers == null
                ? Map.of()
                : headers.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                entry -> entry.getKey().toLowerCase(Locale.ROOT),
                                Map.Entry::getValue,
                                (left, right) -> right
                        ));
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
}
