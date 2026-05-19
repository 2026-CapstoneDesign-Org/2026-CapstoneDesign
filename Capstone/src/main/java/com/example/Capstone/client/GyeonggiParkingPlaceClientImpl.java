package com.example.Capstone.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.Capstone.client.GyeonggiParkingPlaceClient.GyeonggiParkingPlace;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GyeonggiParkingPlaceClientImpl implements GyeonggiParkingPlaceClient {

    private final WebClient webClient;
    private final boolean enabled;
    private final String apiKey;
    private final int pageSize;
    private final int maxPages;
    private final long cacheTtlMillis;

    private volatile long cachedAtMillis;
    private volatile List<GyeonggiParkingPlace> cachedRows = List.of();

    public GyeonggiParkingPlaceClientImpl(
            @Value("${parking-lot.gyeonggi-api.enabled:false}") boolean enabled,
            @Value("${parking-lot.gyeonggi-api.base-url:https://openapi.gg.go.kr/ParkingPlace}") String baseUrl,
            @Value("${parking-lot.gyeonggi-api.key:${GG_PARKING_PLACE_API_KEY:}}") String apiKey,
            @Value("${parking-lot.gyeonggi-api.page-size:1000}") int pageSize,
            @Value("${parking-lot.gyeonggi-api.max-pages:10}") int maxPages,
            @Value("${parking-lot.gyeonggi-api.cache-ttl-ms:86400000}") long cacheTtlMillis
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.pageSize = Math.max(1, Math.min(pageSize, 1000));
        this.maxPages = Math.max(1, maxPages);
        this.cacheTtlMillis = Math.max(0, cacheTtlMillis);
    }

    @Override
    public List<GyeonggiParkingPlace> fetchAllParkingPlaces() {
        if (!enabled || apiKey.isBlank()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        List<GyeonggiParkingPlace> snapshot = cachedRows;
        if (!snapshot.isEmpty() && cacheTtlMillis > 0 && now - cachedAtMillis < cacheTtlMillis) {
            return snapshot;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            snapshot = cachedRows;
            if (!snapshot.isEmpty() && cacheTtlMillis > 0 && now - cachedAtMillis < cacheTtlMillis) {
                return snapshot;
            }

            List<GyeonggiParkingPlace> fetched = fetchFromApi();
            if (!fetched.isEmpty()) {
                cachedRows = List.copyOf(fetched);
                cachedAtMillis = System.currentTimeMillis();
                return fetched;
            }
            return snapshot;
        }
    }

    private List<GyeonggiParkingPlace> fetchFromApi() {
        List<GyeonggiParkingPlace> results = new ArrayList<>();
        int totalCount = Integer.MAX_VALUE;

        for (int page = 1; page <= maxPages && results.size() < totalCount; page++) {
            ParkingPlacePage pageResult = fetchPage(page);
            if (pageResult.totalCount() > 0) {
                totalCount = pageResult.totalCount();
            }
            if (pageResult.rows().isEmpty()) {
                break;
            }
            results.addAll(pageResult.rows());
        }

        return results;
    }

    private ParkingPlacePage fetchPage(int pageIndex) {
        try {
            ParkingPlaceResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("KEY", apiKey)
                            .queryParam("Type", "json")
                            .queryParam("pIndex", pageIndex)
                            .queryParam("pSize", pageSize)
                            .build())
                    .retrieve()
                    .bodyToMono(ParkingPlaceResponse.class)
                    .block();

            return ParkingPlacePage.from(response);
        } catch (Exception exception) {
            log.warn("Gyeonggi ParkingPlace API call failed: {}", exception.getMessage());
            return ParkingPlacePage.empty();
        }
    }

    private record ParkingPlacePage(
            int totalCount,
            List<GyeonggiParkingPlace> rows
    ) {
        static ParkingPlacePage empty() {
            return new ParkingPlacePage(0, List.of());
        }

        static ParkingPlacePage from(ParkingPlaceResponse response) {
            if (response == null || response.parkingPlace() == null) {
                return empty();
            }

            int totalCount = 0;
            List<GyeonggiParkingPlace> rows = new ArrayList<>();
            for (ParkingPlaceSection section : response.parkingPlace()) {
                if (section.head() != null) {
                    for (ParkingPlaceHead head : section.head()) {
                        if (head.totalCount() != null) {
                            totalCount = head.totalCount();
                        }
                    }
                }
                if (section.row() != null) {
                    section.row().stream()
                            .map(ParkingPlaceRow::toParkingPlace)
                            .forEach(rows::add);
                }
            }
            return new ParkingPlacePage(totalCount, rows);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParkingPlaceResponse(
            @JsonProperty("ParkingPlace") List<ParkingPlaceSection> parkingPlace
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParkingPlaceSection(
            List<ParkingPlaceHead> head,
            List<ParkingPlaceRow> row
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParkingPlaceHead(
            @JsonProperty("list_total_count") Integer totalCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParkingPlaceRow(
            @JsonProperty("PARKPLC_MANAGE_NO") String parkingManagementNumber,
            @JsonProperty("PARKPLC_NM") String parkingLotName,
            @JsonProperty("PARKPLC_DIV_NM") String parkingLotDivision,
            @JsonProperty("PARKPLC_TYPE") String parkingLotType,
            @JsonProperty("LOCPLC_ROADNM_ADDR") String roadAddress,
            @JsonProperty("LOCPLC_LOTNO_ADDR") String lotAddress,
            @JsonProperty("PARKNG_COMPRT_PLANE_CNT") Integer parkingCapacity,
            @JsonProperty("SUBTL_IMPLMTN_DIV_NM") String alternateNoDivision,
            @JsonProperty("WKDAY_OPERT_BEGIN_TM") String weekdayBeginTime,
            @JsonProperty("WKDAY_OPERT_END_TM") String weekdayEndTime,
            @JsonProperty("SAT_OPERT_BEGIN_TM") String saturdayBeginTime,
            @JsonProperty("SAT_OPERT_END_TM") String saturdayEndTime,
            @JsonProperty("HOLIDAY_OPERT_BEGIN_TM") String holidayBeginTime,
            @JsonProperty("HOLIDAY_OPERT_END_TM") String holidayEndTime,
            @JsonProperty("REFINE_WGS84_LAT") String lat,
            @JsonProperty("REFINE_WGS84_LOGT") String lng,
            @JsonProperty("PARKNG_BASIS_TM") String basicParkingTime,
            @JsonProperty("PARKNG_BASIS_USE_CHRG") String basicParkingFee,
            @JsonProperty("ADD_UNIT_TM") String additionalUnitTime,
            @JsonProperty("ADD_UNIT_TM2_WITHIN_USE_CHRG") String additionalUnitFee,
            @JsonProperty("CONTCT_NO") String phoneNumber
    ) {
        GyeonggiParkingPlace toParkingPlace() {
            return new GyeonggiParkingPlace(
                    normalizeText(parkingManagementNumber),
                    normalizeText(parkingLotName),
                    normalizeText(parkingLotDivision),
                    normalizeText(parkingLotType),
                    normalizeText(roadAddress),
                    normalizeText(lotAddress),
                    parkingCapacity,
                    normalizeText(alternateNoDivision),
                    operatingHours(weekdayBeginTime, weekdayEndTime),
                    operatingHours(saturdayBeginTime, saturdayEndTime),
                    operatingHours(holidayBeginTime, holidayEndTime),
                    parseDecimal(lat),
                    parseDecimal(lng),
                    parseInteger(basicParkingTime),
                    parseInteger(basicParkingFee),
                    parseInteger(additionalUnitTime),
                    parseInteger(additionalUnitFee),
                    normalizeText(phoneNumber)
            );
        }

        private static String operatingHours(String beginTime, String endTime) {
            String begin = normalizeText(beginTime);
            String end = normalizeText(endTime);
            if (begin == null && end == null) {
                return null;
            }
            if (begin == null) {
                return end;
            }
            if (end == null) {
                return begin;
            }
            return begin + "~" + end;
        }

        private static String normalizeText(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
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
    }
}
