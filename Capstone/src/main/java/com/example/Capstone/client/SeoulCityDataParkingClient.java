package com.example.Capstone.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SeoulCityDataParkingClient {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final int DEFAULT_AREA_RADIUS_METERS = 2_500;

    private final WebClient webClient;
    private final boolean enabled;
    private final String apiKey;
    private final long cacheTtlMillis;
    private final List<SeoulCityDataArea> areas = List.of(
            new SeoulCityDataArea("\uAD11\uD654\uBB38\u00B7\uB355\uC218\uAD81", new BigDecimal("37.57340269"), new BigDecimal("126.97588429"), DEFAULT_AREA_RADIUS_METERS),
            new SeoulCityDataArea("\uBA85\uB3D9 \uAD00\uAD11\uD2B9\uAD6C", new BigDecimal("37.563655"), new BigDecimal("126.983429"), DEFAULT_AREA_RADIUS_METERS),
            new SeoulCityDataArea("\uD64D\uB300 \uAD00\uAD11\uD2B9\uAD6C", new BigDecimal("37.555200"), new BigDecimal("126.922600"), DEFAULT_AREA_RADIUS_METERS),
            new SeoulCityDataArea("\uAC15\uB0A8 MICE \uAD00\uAD11\uD2B9\uAD6C", new BigDecimal("37.511800"), new BigDecimal("127.059200"), DEFAULT_AREA_RADIUS_METERS),
            new SeoulCityDataArea("\uC7A0\uC2E4 \uAD00\uAD11\uD2B9\uAD6C", new BigDecimal("37.513261"), new BigDecimal("127.100133"), DEFAULT_AREA_RADIUS_METERS),
            new SeoulCityDataArea("\uC774\uD0DC\uC6D0 \uAD00\uAD11\uD2B9\uAD6C", new BigDecimal("37.534500"), new BigDecimal("126.994600"), DEFAULT_AREA_RADIUS_METERS)
    );

    private volatile String cachedAreaName;
    private volatile long cachedAtMillis;
    private volatile List<SeoulRealtimeParkingPlace> cachedRows = List.of();

    public SeoulCityDataParkingClient(
            @Value("${parking-lot.seoul-citydata.enabled:false}") boolean enabled,
            @Value("${parking-lot.seoul-citydata.base-url:http://openapi.seoul.go.kr:8088}") String baseUrl,
            @Value("${parking-lot.seoul-citydata.key:${SEOUL_CITYDATA_API_KEY:}}") String apiKey,
            @Value("${parking-lot.seoul-citydata.cache-ttl-ms:30000}") long cacheTtlMillis
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.cacheTtlMillis = Math.max(0, cacheTtlMillis);
    }

    public List<SeoulRealtimeParkingPlace> fetchRealtimeParkingPlaces(BigDecimal lat, BigDecimal lng) {
        if (!enabled || apiKey.isBlank() || lat == null || lng == null) {
            return List.of();
        }

        Optional<String> areaName = resolveAreaName(lat, lng);
        if (areaName.isEmpty()) {
            return List.of();
        }

        return fetchRealtimeParkingPlaces(areaName.get());
    }

    private Optional<String> resolveAreaName(BigDecimal lat, BigDecimal lng) {
        return areas.stream()
                .map(area -> new AreaDistance(area, calculateDistanceMeters(lat, lng, area.lat(), area.lng())))
                .filter(distance -> distance.distanceMeters() <= distance.area().radiusMeters())
                .min(Comparator.comparingInt(AreaDistance::distanceMeters))
                .map(distance -> distance.area().name());
    }

    private List<SeoulRealtimeParkingPlace> fetchRealtimeParkingPlaces(String areaName) {
        long now = System.currentTimeMillis();
        List<SeoulRealtimeParkingPlace> snapshot = cachedRows;
        if (areaName.equals(cachedAreaName)
                && !snapshot.isEmpty()
                && cacheTtlMillis > 0
                && now - cachedAtMillis < cacheTtlMillis) {
            return snapshot;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            snapshot = cachedRows;
            if (areaName.equals(cachedAreaName)
                    && !snapshot.isEmpty()
                    && cacheTtlMillis > 0
                    && now - cachedAtMillis < cacheTtlMillis) {
                return snapshot;
            }

            List<SeoulRealtimeParkingPlace> fetched = fetchFromApi(areaName);
            if (!fetched.isEmpty()) {
                cachedAreaName = areaName;
                cachedRows = List.copyOf(fetched);
                cachedAtMillis = System.currentTimeMillis();
            }
            return fetched;
        }
    }

    private List<SeoulRealtimeParkingPlace> fetchFromApi(String areaName) {
        try {
            SeoulCityDataResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(apiKey, "json", "citydata", "1", "5", areaName)
                            .build())
                    .retrieve()
                    .bodyToMono(SeoulCityDataResponse.class)
                    .block();

            if (response == null || response.cityData() == null || response.cityData().parkingStatuses() == null) {
                return List.of();
            }

            List<SeoulRealtimeParkingPlace> places = new ArrayList<>();
            for (SeoulParkingStatusRow row : response.cityData().parkingStatuses()) {
                SeoulRealtimeParkingPlace place = row.toParkingPlace(response.cityData().areaName());
                if (place.realtimeParkingProvided()) {
                    places.add(place);
                }
            }
            return places;
        } catch (Exception exception) {
            log.warn("Seoul citydata parking API call failed: {}", exception.getMessage());
            return List.of();
        }
    }

    private static int calculateDistanceMeters(
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

    public record SeoulRealtimeParkingPlace(
            String areaName,
            String parkingCode,
            String parkingLotName,
            String parkingLotType,
            String roadAddress,
            String lotAddress,
            Integer parkingCapacity,
            Integer currentParkingCount,
            String currentParkingTime,
            boolean realtimeParkingProvided,
            boolean paid,
            Integer basicParkingFee,
            Integer basicParkingTime,
            Integer additionalUnitFee,
            Integer additionalUnitTime,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeoulCityDataResponse(
            @JsonProperty("CITYDATA") SeoulCityData cityData
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeoulCityData(
            @JsonProperty("AREA_NM") String areaName,
            @JsonProperty("PRK_STTS") List<SeoulParkingStatusRow> parkingStatuses
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeoulParkingStatusRow(
            @JsonProperty("PRK_NM") String parkingLotName,
            @JsonProperty("PRK_CD") String parkingCode,
            @JsonProperty("PRK_TYPE") String parkingLotType,
            @JsonProperty("CPCTY") String parkingCapacity,
            @JsonProperty("CUR_PRK_CNT") String currentParkingCount,
            @JsonProperty("CUR_PRK_TIME") String currentParkingTime,
            @JsonProperty("CUR_PRK_YN") String realtimeParkingProvided,
            @JsonProperty("PAY_YN") String paid,
            @JsonProperty("RATES") String basicParkingFee,
            @JsonProperty("TIME_RATES") String basicParkingTime,
            @JsonProperty("ADD_RATES") String additionalUnitFee,
            @JsonProperty("ADD_TIME_RATES") String additionalUnitTime,
            @JsonProperty("ROAD_ADDR") String roadAddress,
            @JsonProperty("ADDRESS") String lotAddress,
            @JsonProperty("LAT") String lat,
            @JsonProperty("LNG") String lng
    ) {
        SeoulRealtimeParkingPlace toParkingPlace(String areaName) {
            return new SeoulRealtimeParkingPlace(
                    normalizeText(areaName),
                    normalizeText(parkingCode),
                    normalizeText(parkingLotName),
                    normalizeText(parkingLotType),
                    normalizeText(roadAddress),
                    normalizeText(lotAddress),
                    parseInteger(parkingCapacity),
                    parseInteger(currentParkingCount),
                    normalizeText(currentParkingTime),
                    "Y".equalsIgnoreCase(normalizeText(realtimeParkingProvided)),
                    "Y".equalsIgnoreCase(normalizeText(paid)),
                    parseInteger(basicParkingFee),
                    parseInteger(basicParkingTime),
                    parseInteger(additionalUnitFee),
                    parseInteger(additionalUnitTime),
                    parseDecimal(lat),
                    parseDecimal(lng)
            );
        }

        private static String normalizeText(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }

        private static Integer parseInteger(String value) {
            String normalized = normalizeText(value);
            if (normalized == null) {
                return null;
            }
            try {
                return Integer.valueOf(normalized.replace(",", ""));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private static BigDecimal parseDecimal(String value) {
            String normalized = normalizeText(value);
            if (normalized == null) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }

    private record SeoulCityDataArea(
            String name,
            BigDecimal lat,
            BigDecimal lng,
            int radiusMeters
    ) {
    }

    private record AreaDistance(
            SeoulCityDataArea area,
            int distanceMeters
    ) {
    }
}
