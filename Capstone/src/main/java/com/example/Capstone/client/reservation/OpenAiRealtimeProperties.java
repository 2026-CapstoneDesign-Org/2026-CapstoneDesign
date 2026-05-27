package com.example.Capstone.client.reservation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiRealtimeProperties {

    private String apiKey;
    private String projectId;
    private String webhookSecret;
    private Realtime realtime = new Realtime();

    public boolean isRealtimeConfigured() {
        return hasText(apiKey) && realtime != null && hasText(realtime.getModel());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    public static class Realtime {

        private String model;
        private String voice;
        private String instructionsVersion = "reservation-mvp-v1";
        private int sessionTimeoutSeconds = 600;
        private String wsUrl;
        private String sipEndpoint;
    }
}
