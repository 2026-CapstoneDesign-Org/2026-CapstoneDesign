package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.domain.ReservationProviderEventType;

class ReservationProviderEventIdempotencyKeyFactoryTest {

    private final ReservationProviderEventIdempotencyKeyFactory factory =
            new ReservationProviderEventIdempotencyKeyFactory();

    @Test
    @DisplayName("provider event id가 있으면 최우선 idempotency key로 사용한다")
    void createUsesProviderEventIdFirst() {
        ReservationProviderEventCommand command = ReservationProviderEventCommand.builder()
                .provider("MOCK")
                .providerEventId("event-1")
                .providerCallId("mock-100")
                .eventType(ReservationProviderEventType.CALL_STARTED)
                .occurredAt(LocalDateTime.of(2026, 5, 20, 18, 0))
                .rawPayloadHash("hash")
                .signatureVerified(true)
                .build();

        assertThat(factory.create(command)).isEqualTo("MOCK:event-1");
    }

    @Test
    @DisplayName("provider event id가 없으면 call id와 event 정보로 fallback key를 만든다")
    void createUsesFallbackKeyWithoutProviderEventId() {
        ReservationProviderEventCommand command = ReservationProviderEventCommand.builder()
                .provider("MOCK")
                .providerCallId("mock-100")
                .eventType(ReservationProviderEventType.CALL_STARTED)
                .occurredAt(LocalDateTime.of(2026, 5, 20, 18, 0))
                .rawPayloadHash("hash")
                .signatureVerified(true)
                .build();

        assertThat(factory.create(command))
                .startsWith("MOCK:mock-100:CALL_STARTED:")
                .endsWith(":hash");
    }
}
