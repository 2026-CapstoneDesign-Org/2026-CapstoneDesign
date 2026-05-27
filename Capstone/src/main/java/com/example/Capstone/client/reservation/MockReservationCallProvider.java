package com.example.Capstone.client.reservation;

import org.springframework.stereotype.Component;

import com.example.Capstone.domain.ReservationStatus;

@Component
public class MockReservationCallProvider implements ReservationCallProvider {

    @Override
    public ReservationCallStartResult startCall(ReservationCallStartCommand command) {
        return new ReservationCallStartResult(
                ReservationStatus.REQUESTED,
                "MOCK",
                "mock-" + command.reservationId(),
                "MOCK_WAITING_RESULT"
        );
    }
}
