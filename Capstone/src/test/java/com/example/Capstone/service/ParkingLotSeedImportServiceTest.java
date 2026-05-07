package com.example.Capstone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.Capstone.dto.request.ImportParkingLotSeedRequest;
import com.example.Capstone.repository.ParkingLotRepository;

@SpringBootTest
@ActiveProfiles({ "db", "key" })
class ParkingLotSeedImportServiceTest {

    @Autowired
    private ParkingLotSeedImportService parkingLotSeedImportService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanBeforeEach() {
        cleanupTestData();
    }

    @AfterEach
    void cleanAfterEach() {
        cleanupTestData();
    }

    @Test
    @DisplayName("주차장 seed JSON을 생성 또는 갱신 방식으로 적재한다")
    void importsParkingLotSeedRows() throws Exception {
        Path seedPath = tempDir.resolve("parking-lots-seed.json");
        Files.writeString(seedPath, """
                [
                  {
                    "parking_management_number": "TEST-PARK-1",
                    "parking_lot_name": "테스트 공영주차장",
                    "parking_lot_division": "공영",
                    "parking_lot_type": "노외",
                    "road_address": "경기도 용인시 처인구 테스트로 10",
                    "lot_address": "경기도 용인시 처인구 테스트동 1",
                    "parking_capacity": 50,
                    "alternate_no_division": "미시행",
                    "weekday_operating_hours": "00:00~23:59",
                    "saturday_operating_hours": "00:00~23:59",
                    "holiday_operating_hours": "00:00~23:59",
                    "lat": 37.2410000,
                    "lng": 127.1770000,
                    "basic_parking_time": 30,
                    "basic_parking_fee": 500,
                    "additional_unit_time": 10,
                    "additional_unit_fee": 200,
                    "phone_number": "031-000-0000"
                  }
                ]
                """, StandardCharsets.UTF_8);

        var firstResponse = parkingLotSeedImportService.importSeed(new ImportParkingLotSeedRequest(seedPath.toString()));
        var secondResponse = parkingLotSeedImportService.importSeed(new ImportParkingLotSeedRequest(seedPath.toString()));

        assertEquals(1, firstResponse.createdParkingLotCount());
        assertEquals(0, firstResponse.updatedParkingLotCount());
        assertEquals(0, secondResponse.createdParkingLotCount());
        assertEquals(1, secondResponse.updatedParkingLotCount());
        assertEquals(1, parkingLotRepository.findAllByParkingManagementNumber("TEST-PARK-1").size());
    }

    @Test
    @DisplayName("같은 주차장관리번호라도 이름과 주소가 다르면 별도 행으로 적재한다")
    void importsDuplicateManagementNumbersAsSeparateRows() throws Exception {
        Path seedPath = tempDir.resolve("parking-lots-duplicate-management-number-seed.json");
        Files.writeString(seedPath, """
                [
                  {
                    "parking_management_number": "TEST-PARK-DUP",
                    "parking_lot_name": "테스트 주차장 A",
                    "parking_lot_division": "공영",
                    "parking_lot_type": "부설",
                    "road_address": "경기도 용인시 처인구 테스트로 10",
                    "lot_address": "경기도 용인시 처인구 테스트동 1",
                    "parking_capacity": 50,
                    "lat": 37.2410000,
                    "lng": 127.1770000
                  },
                  {
                    "parking_management_number": "TEST-PARK-DUP",
                    "parking_lot_name": "테스트 주차장 B",
                    "parking_lot_division": "공영",
                    "parking_lot_type": "부설",
                    "road_address": "경기도 용인시 처인구 테스트로 20",
                    "lot_address": "경기도 용인시 처인구 테스트동 2",
                    "parking_capacity": 30,
                    "lat": 37.2420000,
                    "lng": 127.1780000
                  }
                ]
                """, StandardCharsets.UTF_8);

        var firstResponse = parkingLotSeedImportService.importSeed(new ImportParkingLotSeedRequest(seedPath.toString()));
        var secondResponse = parkingLotSeedImportService.importSeed(new ImportParkingLotSeedRequest(seedPath.toString()));

        assertEquals(2, firstResponse.createdParkingLotCount());
        assertEquals(0, firstResponse.updatedParkingLotCount());
        assertEquals(0, secondResponse.createdParkingLotCount());
        assertEquals(2, secondResponse.updatedParkingLotCount());
        assertEquals(2, parkingLotRepository.findAllByParkingManagementNumber("TEST-PARK-DUP").size());
    }

    private void cleanupTestData() {
        parkingLotRepository.findAllByParkingManagementNumber("TEST-PARK-1")
                .forEach(parkingLotRepository::delete);
        parkingLotRepository.findAllByParkingManagementNumber("TEST-PARK-DUP")
                .forEach(parkingLotRepository::delete);
    }
}
