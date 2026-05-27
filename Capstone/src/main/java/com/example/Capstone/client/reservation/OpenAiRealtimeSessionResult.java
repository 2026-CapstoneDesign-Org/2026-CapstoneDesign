package com.example.Capstone.client.reservation;

public record OpenAiRealtimeSessionResult(
        boolean enabled,
        String providerSessionId,
        String providerStatus,
        String message
) {
    public static OpenAiRealtimeSessionResult disabled(String message) {
        return new OpenAiRealtimeSessionResult(false, null, "OPENAI_REALTIME_DISABLED", message);
    }
}
