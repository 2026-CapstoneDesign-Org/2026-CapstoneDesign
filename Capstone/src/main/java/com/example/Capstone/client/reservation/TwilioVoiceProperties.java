package com.example.Capstone.client.reservation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "twilio")
public class TwilioVoiceProperties {

    private String accountSid;
    private String authToken;
    private String fromNumber;
    private String statusCallbackUrl;
    private String voiceWebhookUrl;
    private String mediaStreamUrl;
    private boolean webhookSignatureEnabled = true;
    private List<String> dialingCountryAllowlist = new ArrayList<>();

    public boolean isConfigured() {
        return hasText(accountSid)
                && hasText(authToken)
                && hasText(fromNumber)
                && hasText(statusCallbackUrl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
