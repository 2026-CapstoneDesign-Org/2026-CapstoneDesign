package com.example.Capstone.dto.request;

import java.math.BigDecimal;

public record UpdateParkingLotRequest(
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
        String phoneNumber
) {
}
