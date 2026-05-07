package com.example.Capstone.dto.response;

import java.math.BigDecimal;

import com.example.Capstone.domain.ParkingLot;

public record ParkingLotResponse(
        Long id,
        String parkingLotName,
        String parkingLotDivision,
        String parkingLotType,
        String roadAddress,
        String lotAddress,
        Integer parkingCapacity,
        String alternateNoDivision,
        String weekdayOperatingHours,
        String saturdayOperatingHours,
        String holidayOperatingHours,
        BigDecimal lat,
        BigDecimal lng,
        Integer basicParkingTime,
        Integer basicParkingFee,
        Integer additionalUnitTime,
        Integer additionalUnitFee,
        String phoneNumber,
        Integer distanceMeters
) {
    public static ParkingLotResponse from(ParkingLot parkingLot) {
        return from(parkingLot, null);
    }

    public static ParkingLotResponse from(ParkingLot parkingLot, Integer distanceMeters) {
        return new ParkingLotResponse(
                parkingLot.getId(),
                parkingLot.getParkingLotName(),
                parkingLot.getParkingLotDivision(),
                parkingLot.getParkingLotType(),
                parkingLot.getRoadAddress(),
                parkingLot.getLotAddress(),
                parkingLot.getParkingCapacity(),
                parkingLot.getAlternateNoDivision(),
                parkingLot.getWeekdayOperatingHours(),
                parkingLot.getSaturdayOperatingHours(),
                parkingLot.getHolidayOperatingHours(),
                parkingLot.getLat(),
                parkingLot.getLng(),
                parkingLot.getBasicParkingTime(),
                parkingLot.getBasicParkingFee(),
                parkingLot.getAdditionalUnitTime(),
                parkingLot.getAdditionalUnitFee(),
                parkingLot.getPhoneNumber(),
                distanceMeters
        );
    }

    public static ParkingLotResponse from(ParkingLot parkingLot, int distanceMeters) {
        return from(parkingLot, Integer.valueOf(distanceMeters));
    }
}
