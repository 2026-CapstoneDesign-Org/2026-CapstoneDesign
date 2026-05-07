package com.example.Capstone.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.domain.ParkingLot;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.request.CreateParkingLotRequest;
import com.example.Capstone.dto.request.UpdateParkingLotRequest;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.repository.ParkingLotRepository;
import com.example.Capstone.repository.RestaurantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParkingLotService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_CRUD_LIMIT = 50;
    private static final int MAX_CRUD_LIMIT = 200;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final ParkingLotRepository parkingLotRepository;
    private final RestaurantRepository restaurantRepository;

    public List<ParkingLotResponse> getParkingLots(String parkingLotDivision, Integer limit) {
        int normalizedLimit = normalizeCrudLimit(limit);
        return parkingLotRepository.findAll().stream()
                .filter(parkingLot -> parkingLotDivision == null
                        || parkingLotDivision.isBlank()
                        || parkingLot.getParkingLotDivision().equals(parkingLotDivision.trim()))
                .sorted(Comparator
                        .comparing(ParkingLot::getParkingLotName)
                        .thenComparing(ParkingLot::getId))
                .limit(normalizedLimit)
                .map(ParkingLotResponse::from)
                .toList();
    }

    public ParkingLotResponse getParkingLot(Long parkingLotId) {
        return ParkingLotResponse.from(findParkingLot(parkingLotId));
    }

    @Transactional
    public ParkingLotResponse createParkingLot(CreateParkingLotRequest request) {
        validateCreateRequest(request);

        ParkingLot parkingLot = parkingLotRepository.save(ParkingLot.builder()
                .parkingManagementNumber(request.parkingManagementNumber().trim())
                .parkingLotName(request.parkingLotName().trim())
                .parkingLotDivision(request.parkingLotDivision().trim())
                .parkingLotType(request.parkingLotType().trim())
                .roadAddress(normalizeText(request.roadAddress()))
                .lotAddress(normalizeText(request.lotAddress()))
                .parkingCapacity(request.parkingCapacity())
                .alternateNoDivision(normalizeText(request.alternateNoDivision()))
                .weekdayOperatingHours(normalizeText(request.weekdayOperatingHours()))
                .saturdayOperatingHours(normalizeText(request.saturdayOperatingHours()))
                .holidayOperatingHours(normalizeText(request.holidayOperatingHours()))
                .lat(request.lat())
                .lng(request.lng())
                .basicParkingTime(request.basicParkingTime())
                .basicParkingFee(request.basicParkingFee())
                .additionalUnitTime(request.additionalUnitTime())
                .additionalUnitFee(request.additionalUnitFee())
                .phoneNumber(normalizeText(request.phoneNumber()))
                .build());

        return ParkingLotResponse.from(parkingLot);
    }

    @Transactional
    public ParkingLotResponse updateParkingLot(Long parkingLotId, UpdateParkingLotRequest request) {
        validateUpdateRequest(request);
        ParkingLot parkingLot = findParkingLot(parkingLotId);
        parkingLot.updateFromSeed(
                request.parkingLotName().trim(),
                request.parkingLotDivision().trim(),
                request.parkingLotType().trim(),
                normalizeText(request.roadAddress()),
                normalizeText(request.lotAddress()),
                request.parkingCapacity(),
                normalizeText(request.alternateNoDivision()),
                normalizeText(request.weekdayOperatingHours()),
                normalizeText(request.saturdayOperatingHours()),
                normalizeText(request.holidayOperatingHours()),
                request.lat(),
                request.lng(),
                request.basicParkingTime(),
                request.basicParkingFee(),
                request.additionalUnitTime(),
                request.additionalUnitFee(),
                normalizeText(request.phoneNumber())
        );
        return ParkingLotResponse.from(parkingLot);
    }

    @Transactional
    public void deleteParkingLot(Long parkingLotId) {
        parkingLotRepository.delete(findParkingLot(parkingLotId));
    }

    public List<ParkingLotResponse> getParkingLotsByDistance(
            Long restaurantId,
            Integer limit,
            String parkingLotDivision
    ) {
        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("restaurant not found."));

        if (restaurant.getLat() == null || restaurant.getLng() == null) {
            throw new IllegalArgumentException("restaurant coordinate is required.");
        }

        int normalizedLimit = normalizeLimit(limit);
        List<ParkingLot> parkingLots = resolveParkingLots(parkingLotDivision);

        return parkingLots.stream()
                .map(parkingLot -> ParkingLotDistance.of(
                        parkingLot,
                        calculateDistanceMeters(
                                restaurant.getLat(),
                                restaurant.getLng(),
                                parkingLot.getLat(),
                                parkingLot.getLng()
                        )
                ))
                .sorted(Comparator
                        .comparingInt(ParkingLotDistance::distanceMeters)
                        .thenComparing(distance -> distance.parkingLot().getParkingLotName())
                        .thenComparing(
                                distance -> distance.parkingLot().getId(),
                                Comparator.nullsLast(Long::compareTo)
                        ))
                .limit(normalizedLimit)
                .map(distance -> ParkingLotResponse.from(distance.parkingLot(), distance.distanceMeters()))
                .toList();
    }

    static int calculateDistanceMeters(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng
    ) {
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double deltaLat = Math.toRadians(toLat.doubleValue() - fromLat.doubleValue());
        double deltaLng = Math.toRadians(toLng.doubleValue() - fromLng.doubleValue());

        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLng = Math.sin(deltaLng / 2.0);
        double a = sinLat * sinLat
                + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }

    private List<ParkingLot> resolveParkingLots(String parkingLotDivision) {
        if (parkingLotDivision == null || parkingLotDivision.isBlank()) {
            return parkingLotRepository.findAllByLatIsNotNullAndLngIsNotNull();
        }
        return parkingLotRepository.findAllByParkingLotDivisionAndLatIsNotNullAndLngIsNotNull(parkingLotDivision.trim());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1.");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeCrudLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CRUD_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1.");
        }
        return Math.min(limit, MAX_CRUD_LIMIT);
    }

    private ParkingLot findParkingLot(Long parkingLotId) {
        return parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new EntityNotFoundException("parking lot not found."));
    }

    private void validateCreateRequest(CreateParkingLotRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        requireText(request.parkingManagementNumber(), "parkingManagementNumber");
        requireText(request.parkingLotName(), "parkingLotName");
        requireText(request.parkingLotDivision(), "parkingLotDivision");
        requireText(request.parkingLotType(), "parkingLotType");
    }

    private void validateUpdateRequest(UpdateParkingLotRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        requireText(request.parkingLotName(), "parkingLotName");
        requireText(request.parkingLotDivision(), "parkingLotDivision");
        requireText(request.parkingLotType(), "parkingLotType");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ParkingLotDistance(
            ParkingLot parkingLot,
            int distanceMeters
    ) {
        static ParkingLotDistance of(ParkingLot parkingLot, int distanceMeters) {
            return new ParkingLotDistance(parkingLot, distanceMeters);
        }
    }
}
