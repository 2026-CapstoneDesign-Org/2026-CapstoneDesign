package com.example.Capstone.client.reservation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClawOpsCreateCallRequest(
        @JsonProperty("To")
        String to,
        @JsonProperty("From")
        String from,
        @JsonProperty("StatusCallback")
        String statusCallback,
        @JsonProperty("StatusCallbackEvent")
        String statusCallbackEvent,
        @JsonProperty("Timeout")
        Integer timeout
) {
    private static final String DEFAULT_STATUS_CALLBACK_EVENTS = "initiated ringing answered completed";

    public static ClawOpsCreateCallRequest from(
            PhoneProviderCallStartCommand command,
            ClawOpsProperties properties
    ) {
        return new ClawOpsCreateCallRequest(
                command.toPhoneNumber(),
                properties.getFromNumber(),
                properties.getStatusCallbackUrl(),
                DEFAULT_STATUS_CALLBACK_EVENTS,
                properties.getCallTimeoutSeconds()
        );
    }
}
