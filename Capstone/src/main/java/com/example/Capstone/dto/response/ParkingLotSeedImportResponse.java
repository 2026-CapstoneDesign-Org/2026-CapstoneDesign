package com.example.Capstone.dto.response;

public record ParkingLotSeedImportResponse(
        String parkingLotsFilePath,
        int totalParkingLotCount,
        int createdParkingLotCount,
        int updatedParkingLotCount
) {
}
