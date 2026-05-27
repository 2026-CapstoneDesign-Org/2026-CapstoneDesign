package com.example.Capstone.client.reservation;

public enum ReservationProviderMode {
    MOCK,
    NOOP,
    TWILIO,
    OPENAI_SIP,
    CLAWOPS,
    GENERIC;

    public boolean isExternal() {
        return this == TWILIO || this == OPENAI_SIP || this == CLAWOPS || this == GENERIC;
    }
}
