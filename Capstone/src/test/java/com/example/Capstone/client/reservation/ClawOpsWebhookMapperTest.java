package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.Capstone.domain.ReservationProviderEventType;

class ClawOpsWebhookMapperTest {

    private final ClawOpsWebhookMapper mapper = new ClawOpsWebhookMapper();

    @Test
    @DisplayName("ClawOps answered status callback을 내부 표준 event command로 변환한다")
    void mapAnsweredStatusCallback() {
        String rawPayload = "CallId=CA123"
                + "&AccountId=AC123"
                + "&ReservationId=100"
                + "&CallStatus=answered"
                + "&Timestamp=2026-05-24T10%3A15%3A30Z"
                + "&From=+15550100003"
                + "&To=+15550100001"
                + "&Direction=outbound";

        ReservationProviderEventCommand command = mapper.toCommand(rawPayload);

        assertThat(command.getProvider()).isEqualTo("CLAWOPS");
        assertThat(command.getProviderCallId()).isEqualTo("CA123");
        assertThat(command.getReservationId()).isEqualTo(100L);
        assertThat(command.getEventType()).isEqualTo(ReservationProviderEventType.CALL_CONNECTED);
        assertThat(command.getProviderStatus()).isEqualTo("answered");
        assertThat(command.isRetryable()).isFalse();
        assertThat(command.getRawPayload()).isEqualTo(rawPayload);
        assertThat(command.isSignatureVerified()).isTrue();
    }

    @Test
    @DisplayName("ClawOps failed status callback은 retry 가능한 연결 실패 event로 변환한다")
    void mapFailedStatusCallbackAsRetryableConnectionFailure() {
        String rawPayload = "CallId=CA123"
                + "&ReservationId=100"
                + "&CallStatus=failed"
                + "&Timestamp=2026-05-24T10%3A15%3A30Z"
                + "&ErrorMessage=temporary+provider+failure";

        ReservationProviderEventCommand command = mapper.toCommand(rawPayload);

        assertThat(command.getEventType()).isEqualTo(ReservationProviderEventType.CALL_CONNECTION_FAILED);
        assertThat(command.isRetryable()).isTrue();
        assertThat(command.getFailureReason()).isEqualTo("temporary provider failure");
    }

    @Test
    @DisplayName("지원하지 않는 ClawOps webhook event는 예약 상태 command로 변환하지 않는다")
    void rejectUnsupportedWebhookEvent() {
        String rawPayload = "CallId=CA123"
                + "&ReservationId=100"
                + "&Event=summary.completed"
                + "&Timestamp=2026-05-24T10%3A15%3A30Z";

        assertThatThrownBy(() -> mapper.toCommand(rawPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 ClawOps event/status");
    }
}
