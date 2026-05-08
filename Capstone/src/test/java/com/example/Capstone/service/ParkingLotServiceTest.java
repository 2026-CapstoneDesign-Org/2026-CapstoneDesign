package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Capstone.domain.ParkingLot;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.dto.request.CreateParkingLotRequest;
import com.example.Capstone.dto.request.UpdateParkingLotRequest;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.repository.ParkingLotRepository;
import com.example.Capstone.repository.RestaurantRepository;

@ExtendWith(MockitoExtension.class)
class ParkingLotServiceTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private ParkingLotService parkingLotService;

    @Test
    @DisplayName("주차장을 생성, 조회, 수정, 삭제한다")
    void handlesParkingLotCrud() {
        ParkingLot created = parkingLot("CRUD-1", "생성 주차장", "37.2412000", "127.1777000");
        ParkingLot updated = parkingLot("CRUD-1", "수정 주차장", "37.2412000", "127.1777000");

        when(parkingLotRepository.save(anyParkingLot()))
                .thenReturn(created);
        when(parkingLotRepository.findAll())
                .thenReturn(List.of(created));
        when(parkingLotRepository.findById(1L))
                .thenReturn(Optional.of(created), Optional.of(updated), Optional.of(updated));

        ParkingLotResponse createResponse = parkingLotService.createParkingLot(new CreateParkingLotRequest(
                "CRUD-1",
                "생성 주차장",
                "공영",
                "노외",
                "경기도 용인시 처인구 테스트로 10",
                null,
                50,
                "미시행",
                "00:00~23:59",
                "00:00~23:59",
                "00:00~23:59",
                new BigDecimal("37.2412000"),
                new BigDecimal("127.1777000"),
                30,
                500,
                10,
                200,
                "031-000-0000"
        ));
        List<ParkingLotResponse> listResponse = parkingLotService.getParkingLots("공영", 10);
        ParkingLotResponse updateResponse = parkingLotService.updateParkingLot(1L, new UpdateParkingLotRequest(
                "수정 주차장",
                "공영",
                "노외",
                "경기도 용인시 처인구 수정로 10",
                null,
                60,
                "미시행",
                "09:00~18:00",
                "09:00~18:00",
                "00:00~23:59",
                new BigDecimal("37.2412000"),
                new BigDecimal("127.1777000"),
                30,
                600,
                10,
                300,
                "031-111-1111"
        ));
        ParkingLotResponse getResponse = parkingLotService.getParkingLot(1L);
        parkingLotService.deleteParkingLot(1L);

        assertEquals("생성 주차장", createResponse.parkingLotName());
        assertEquals(1, listResponse.size());
        assertEquals("수정 주차장", updateResponse.parkingLotName());
        assertEquals("수정 주차장", getResponse.parkingLotName());
    }

    @Test
    @DisplayName("restaurant coordinate 기준 거리 오름차순으로 주차장을 반환한다")
    void returnsParkingLotsSortedByDistance() {
        Restaurant restaurant = restaurant("37.2410864", "127.1775537");
        ParkingLot near = parkingLot("P-1", "가까운 공영주차장", "37.2412000", "127.1777000");
        ParkingLot far = parkingLot("P-2", "먼 공영주차장", "37.2600000", "127.2000000");

        when(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(1L))
                .thenReturn(Optional.of(restaurant));
        when(parkingLotRepository.findAllByLatIsNotNullAndLngIsNotNull())
                .thenReturn(List.of(far, near));

        List<ParkingLotResponse> responses = parkingLotService.getParkingLotsByDistance(1L, 10, null);

        assertEquals(2, responses.size());
        assertEquals("가까운 공영주차장", responses.get(0).parkingLotName());
        assertEquals("먼 공영주차장", responses.get(1).parkingLotName());
        assertEquals(true, responses.get(0).distanceMeters() < responses.get(1).distanceMeters());
    }

    @Test
    @DisplayName("parkingLotDivision이 있으면 해당 구분만 조회한다")
    void filtersByParkingLotDivision() {
        Restaurant restaurant = restaurant("37.2410864", "127.1775537");
        ParkingLot publicLot = parkingLot("P-1", "공영주차장", "37.2412000", "127.1777000");

        when(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(1L))
                .thenReturn(Optional.of(restaurant));
        when(parkingLotRepository.findAllByParkingLotDivisionAndLatIsNotNullAndLngIsNotNull("공영"))
                .thenReturn(List.of(publicLot));

        List<ParkingLotResponse> responses = parkingLotService.getParkingLotsByDistance(1L, 5, "공영");

        assertEquals(1, responses.size());
        assertEquals("공영", responses.get(0).parkingLotDivision());
    }

    @Test
    @DisplayName("식당 좌표가 없으면 거리 계산을 거부한다")
    void rejectsRestaurantWithoutCoordinate() {
        Restaurant restaurant = restaurant(null, null);

        when(restaurantRepository.findByIdAndIsDeletedFalseAndIsHiddenFalse(1L))
                .thenReturn(Optional.of(restaurant));

        assertThrows(IllegalArgumentException.class,
                () -> parkingLotService.getParkingLotsByDistance(1L, 5, null));
    }

    private Restaurant restaurant(String lat, String lng) {
        return Restaurant.builder()
                .name("테스트 식당")
                .address("경기 용인시 처인구 테스트로 1")
                .regionName("용인시")
                .lat(lat == null ? null : new BigDecimal(lat))
                .lng(lng == null ? null : new BigDecimal(lng))
                .build();
    }

    private ParkingLot parkingLot(String managementNumber, String name, String lat, String lng) {
        return ParkingLot.builder()
                .parkingManagementNumber(managementNumber)
                .parkingLotName(name)
                .parkingLotDivision("공영")
                .parkingLotType("노외")
                .roadAddress("경기 용인시 처인구 테스트로 10")
                .parkingCapacity(50)
                .alternateNoDivision("미시행")
                .weekdayOperatingHours("00:00~23:59")
                .saturdayOperatingHours("00:00~23:59")
                .holidayOperatingHours("00:00~23:59")
                .lat(new BigDecimal(lat))
                .lng(new BigDecimal(lng))
                .basicParkingTime(30)
                .basicParkingFee(500)
                .additionalUnitTime(10)
                .additionalUnitFee(200)
                .phoneNumber("031-000-0000")
                .build();
    }

    private ParkingLot anyParkingLot() {
        return org.mockito.ArgumentMatchers.any(ParkingLot.class);
    }
}
