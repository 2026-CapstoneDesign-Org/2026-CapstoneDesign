package com.example.Capstone.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.ParkingLot;
import com.example.Capstone.dto.request.ImportParkingLotSeedRequest;
import com.example.Capstone.dto.response.ParkingLotSeedImportResponse;
import com.example.Capstone.repository.ParkingLotRepository;
import com.example.Capstone.service.seed.RestaurantSeedFileLoader;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingLotSeedImportService {

    private static final Path DEFAULT_PARKING_LOTS_FILE_PATH = Path.of("seed-data", "parking-lots-seed.json");

    private final ParkingLotRepository parkingLotRepository;
    private final RestaurantSeedFileLoader seedFileLoader;

    @Transactional
    public ParkingLotSeedImportResponse importSeed(ImportParkingLotSeedRequest request) {
        Path parkingLotsPath = seedFileLoader.resolvePath(
                request == null ? null : request.parkingLotsFilePath(),
                DEFAULT_PARKING_LOTS_FILE_PATH
        );

        List<ParkingLotSeedRow> rows = seedFileLoader.readRows(
                parkingLotsPath,
                new TypeReference<List<ParkingLotSeedRow>>() {}
        );

        int createdCount = 0;
        int updatedCount = 0;

        for (ParkingLotSeedRow row : rows) {
            validateRow(row);
            List<ParkingLot> existing = parkingLotRepository.findSeedMatches(
                    requiredText(row.parkingManagementNumber()),
                    requiredText(row.parkingLotName()),
                    requiredText(row.parkingLotDivision()),
                    requiredText(row.parkingLotType()),
                    normalizeText(row.roadAddress()),
                    normalizeText(row.lotAddress()),
                    row.lat(),
                    row.lng()
            );

            if (!existing.isEmpty()) {
                updateParkingLot(existing.get(0), row);
                updatedCount += 1;
            } else {
                parkingLotRepository.save(createParkingLot(row));
                createdCount += 1;
            }
        }

        return new ParkingLotSeedImportResponse(
                parkingLotsPath.toString(),
                rows.size(),
                createdCount,
                updatedCount
        );
    }

    private ParkingLot createParkingLot(ParkingLotSeedRow row) {
        return ParkingLot.builder()
                .parkingManagementNumber(requiredText(row.parkingManagementNumber()))
                .parkingLotName(requiredText(row.parkingLotName()))
                .parkingLotDivision(requiredText(row.parkingLotDivision()))
                .parkingLotType(requiredText(row.parkingLotType()))
                .roadAddress(normalizeText(row.roadAddress()))
                .lotAddress(normalizeText(row.lotAddress()))
                .parkingCapacity(row.parkingCapacity())
                .alternateNoDivision(normalizeText(row.alternateNoDivision()))
                .weekdayOperatingHours(normalizeText(row.weekdayOperatingHours()))
                .saturdayOperatingHours(normalizeText(row.saturdayOperatingHours()))
                .holidayOperatingHours(normalizeText(row.holidayOperatingHours()))
                .lat(row.lat())
                .lng(row.lng())
                .basicParkingTime(row.basicParkingTime())
                .basicParkingFee(row.basicParkingFee())
                .additionalUnitTime(row.additionalUnitTime())
                .additionalUnitFee(row.additionalUnitFee())
                .phoneNumber(normalizeText(row.phoneNumber()))
                .build();
    }

    private void updateParkingLot(ParkingLot parkingLot, ParkingLotSeedRow row) {
        parkingLot.updateFromSeed(
                requiredText(row.parkingLotName()),
                requiredText(row.parkingLotDivision()),
                requiredText(row.parkingLotType()),
                normalizeText(row.roadAddress()),
                normalizeText(row.lotAddress()),
                row.parkingCapacity(),
                normalizeText(row.alternateNoDivision()),
                normalizeText(row.weekdayOperatingHours()),
                normalizeText(row.saturdayOperatingHours()),
                normalizeText(row.holidayOperatingHours()),
                row.lat(),
                row.lng(),
                row.basicParkingTime(),
                row.basicParkingFee(),
                row.additionalUnitTime(),
                row.additionalUnitFee(),
                normalizeText(row.phoneNumber())
        );
    }

    private void validateRow(ParkingLotSeedRow row) {
        if (row.parkingManagementNumber() == null || row.parkingManagementNumber().isBlank()) {
            throw new IllegalArgumentException("parking_management_number is required.");
        }
        if (row.parkingLotName() == null || row.parkingLotName().isBlank()) {
            throw new IllegalArgumentException("parking_lot_name is required.");
        }
        if (row.parkingLotDivision() == null || row.parkingLotDivision().isBlank()) {
            throw new IllegalArgumentException("parking_lot_division is required.");
        }
        if (row.parkingLotType() == null || row.parkingLotType().isBlank()) {
            throw new IllegalArgumentException("parking_lot_type is required.");
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requiredText(String value) {
        return value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParkingLotSeedRow(
            @JsonProperty("parking_management_number") String parkingManagementNumber,
            @JsonProperty("parking_lot_name") String parkingLotName,
            @JsonProperty("parking_lot_division") String parkingLotDivision,
            @JsonProperty("parking_lot_type") String parkingLotType,
            @JsonProperty("road_address") String roadAddress,
            @JsonProperty("lot_address") String lotAddress,
            @JsonProperty("parking_capacity") Integer parkingCapacity,
            @JsonProperty("alternate_no_division") String alternateNoDivision,
            @JsonProperty("weekday_operating_hours") String weekdayOperatingHours,
            @JsonProperty("saturday_operating_hours") String saturdayOperatingHours,
            @JsonProperty("holiday_operating_hours") String holidayOperatingHours,
            @JsonProperty("lat") BigDecimal lat,
            @JsonProperty("lng") BigDecimal lng,
            @JsonProperty("basic_parking_time") Integer basicParkingTime,
            @JsonProperty("basic_parking_fee") Integer basicParkingFee,
            @JsonProperty("additional_unit_time") Integer additionalUnitTime,
            @JsonProperty("additional_unit_fee") Integer additionalUnitFee,
            @JsonProperty("phone_number") String phoneNumber
    ) {
    }
}
