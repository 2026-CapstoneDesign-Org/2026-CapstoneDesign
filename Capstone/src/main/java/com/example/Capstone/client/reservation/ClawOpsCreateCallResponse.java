package com.example.Capstone.client.reservation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClawOpsCreateCallResponse(
        @JsonAlias({ "call_id", "callId", "CallId" })
        String callId,
        @JsonAlias({ "status", "call_status", "callStatus", "CallStatus" })
        String status
) {
}
