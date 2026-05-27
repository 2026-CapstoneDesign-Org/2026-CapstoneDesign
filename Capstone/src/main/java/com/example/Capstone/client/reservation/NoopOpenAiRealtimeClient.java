package com.example.Capstone.client.reservation;

import org.springframework.stereotype.Component;

@Component
public class NoopOpenAiRealtimeClient implements OpenAiRealtimeClient {

    @Override
    public OpenAiRealtimeSessionResult prepareReservationSession(OpenAiRealtimeSessionCommand command) {
        return OpenAiRealtimeSessionResult.disabled("OpenAI Realtime client는 아직 no-op 모드입니다.");
    }
}
