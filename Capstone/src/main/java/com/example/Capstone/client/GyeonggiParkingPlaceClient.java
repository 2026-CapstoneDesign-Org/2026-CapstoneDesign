package com.example.Capstone.client;

import java.math.BigDecimal;
import java.util.List;

public interface GyeonggiParkingPlaceClient {

    List<GyeonggiParkingPlace> fetchAllParkingPlaces();

    record GyeonggiParkingPlace(
            String parkingManagementNumber,
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
}
