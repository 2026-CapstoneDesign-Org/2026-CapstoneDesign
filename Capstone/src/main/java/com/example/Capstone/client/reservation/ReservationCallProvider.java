package com.example.Capstone.client.reservation;

public interface ReservationCallProvider {

    ReservationCallStartResult startCall(ReservationCallStartCommand command);
}
