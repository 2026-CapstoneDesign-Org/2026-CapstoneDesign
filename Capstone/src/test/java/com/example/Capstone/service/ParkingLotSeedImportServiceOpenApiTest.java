package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Capstone.client.GyeonggiParkingPlaceClient;
import com.example.Capstone.client.GyeonggiParkingPlaceClient.GyeonggiParkingPlace;
import com.example.Capstone.domain.ParkingLot;
import com.example.Capstone.repository.ParkingLotRepository;
import com.example.Capstone.service.seed.RestaurantSeedFileLoader;

@ExtendWith(MockitoExtension.class)
class ParkingLotSeedImportServiceOpenApiTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private RestaurantSeedFileLoader seedFileLoader;

    @Mock
    private GyeonggiParkingPlaceClient gyeonggiParkingPlaceClient;

    @InjectMocks
    private ParkingLotSeedImportService parkingLotSeedImportService;

    @Test
    @DisplayName("경기도 OpenAPI 주차장 데이터를 seed upsert 경로로 적재한다")
    void importsGyeonggiOpenApiRows() {
        GyeonggiParkingPlace place = new GyeonggiParkingPlace(
                "236-1-000023",
                "시가지 노상주차장",
                "공영",
                "노상",
                "경기도 포천시 영북면 영북로 168",
                "경기도 포천시 영북면 운천리 340-3",
                202,
                "미시행",
                "00:00~23:59",
                "00:00~23:59",
                "00:00~23:59",
                new BigDecimal("38.0892552378"),
                new BigDecimal("127.2751533479"),
                30,
                null,
                null,
                null,
                "031-538-3493"
        );

        when(gyeonggiParkingPlaceClient.fetchAllParkingPlaces())
                .thenReturn(List.of(place));
        when(parkingLotRepository.findSeedMatches(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        var response = parkingLotSeedImportService.importGyeonggiOpenApiSeed();

        assertEquals("gyeonggi-openapi:ParkingPlace", response.parkingLotsFilePath());
        assertEquals(1, response.totalParkingLotCount());
        assertEquals(1, response.createdParkingLotCount());
        assertEquals(0, response.updatedParkingLotCount());
        verify(parkingLotRepository).save(any(ParkingLot.class));
    }
}
