package com.example.Capstone.client.reservation;

public interface OpenAiRealtimeClient {

    OpenAiRealtimeSessionResult prepareReservationSession(OpenAiRealtimeSessionCommand command);
}
