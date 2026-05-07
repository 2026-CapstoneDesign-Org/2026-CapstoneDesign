package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.Capstone.common.jwt.JwtProvider;
import com.example.Capstone.domain.ParkingLot;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.CreateParkingLotRequest;
import com.example.Capstone.dto.request.UpdateParkingLotRequest;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.repository.ParkingLotRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.UserRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "db", "key" })
class ParkingLotE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBeforeEach() {
        cleanupE2eData();
    }

    @AfterEach
    void cleanAfterEach() {
        cleanupE2eData();
    }

    @Test
    @DisplayName("JWT 인증 HTTP 요청으로 주차장 CRUD를 수행한다")
    void handlesParkingLotCrudOverHttpWithJwt() {
        User user = saveUser("parking-crud");
        String managementNumber = unique("e2e-park-crud");

        ResponseEntity<ParkingLotResponse> createResponse = restTemplate.exchange(
                "/parking-lots",
                HttpMethod.POST,
                authenticatedEntity(user.getId(), new CreateParkingLotRequest(
                        managementNumber,
                        "e2e-parking-crud",
                        "공영",
                        "노외",
                        "경기도 용인시 처인구 테스트로 10",
                        "경기도 용인시 처인구 테스트동 1",
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
                )),
                ParkingLotResponse.class
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        Long parkingLotId = createResponse.getBody().id();

        ResponseEntity<ParkingLotResponse> updateResponse = restTemplate.exchange(
                "/parking-lots/{parkingLotId}",
                HttpMethod.PATCH,
                authenticatedEntity(user.getId(), new UpdateParkingLotRequest(
                        "e2e-parking-crud-updated",
                        "공영",
                        "노외",
                        "경기도 용인시 처인구 수정로 10",
                        "경기도 용인시 처인구 수정동 1",
                        60,
                        "미시행",
                        "09:00~18:00",
                        "09:00~18:00",
                        "00:00~23:59",
                        new BigDecimal("37.2413000"),
                        new BigDecimal("127.1778000"),
                        30,
                        600,
                        10,
                        300,
                        "031-111-1111"
                )),
                ParkingLotResponse.class,
                parkingLotId
        );

        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertNotNull(updateResponse.getBody());
        assertEquals("e2e-parking-crud-updated", updateResponse.getBody().parkingLotName());

        ResponseEntity<ParkingLotResponse> getResponse = restTemplate.exchange(
                "/parking-lots/{parkingLotId}",
                HttpMethod.GET,
                authenticatedEntity(user.getId()),
                ParkingLotResponse.class,
                parkingLotId
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());
        assertEquals("e2e-parking-crud-updated", getResponse.getBody().parkingLotName());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/parking-lots/{parkingLotId}",
                HttpMethod.DELETE,
                authenticatedEntity(user.getId()),
                Void.class,
                parkingLotId
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
    }

    @Test
    @DisplayName("JWT 인증 HTTP 요청으로 식당 기준 주변 주차장을 거리순 조회한다")
    void getsParkingLotsByRestaurantDistanceOverHttpWithJwt() {
        User user = saveUser("parking");
        Restaurant restaurant = saveRestaurant();
        saveParkingLot("e2e-parking-near", "37.2412000", "127.1777000");
        saveParkingLot("e2e-parking-far", "37.2600000", "127.2000000");

        ResponseEntity<ParkingLotResponse[]> response = restTemplate.exchange(
                "/restaurants/{restaurantId}/parking-lots?limit=2&parkingLotDivision={parkingLotDivision}",
                HttpMethod.GET,
                authenticatedEntity(user.getId()),
                ParkingLotResponse[].class,
                restaurant.getId(),
                "E2E"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);
        assertEquals("e2e-parking-near", response.getBody()[0].parkingLotName());
        assertEquals("e2e-parking-far", response.getBody()[1].parkingLotName());
        assertTrue(response.getBody()[0].distanceMeters() < response.getBody()[1].distanceMeters());
        assertEquals("00:00~23:59", response.getBody()[0].weekdayOperatingHours());
    }

    private HttpEntity<Void> authenticatedEntity(Long userId) {
        String token = jwtProvider.generateAccessToken(userId, User.Role.USER.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> authenticatedEntity(Long userId, T body) {
        String token = jwtProvider.generateAccessToken(userId, User.Role.USER.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private User saveUser(String suffix) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(unique("provider-e2e-" + suffix))
                .nickname(unique("nick-e2e-" + suffix))
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }

    private Restaurant saveRestaurant() {
        Restaurant restaurant = Restaurant.builder()
                .name(unique("e2e-parking-restaurant"))
                .address("경기도 용인시 처인구 테스트로 1")
                .roadAddress("경기도 용인시 처인구 테스트로 1")
                .categoryName("한식")
                .regionName("용인시")
                .lat(new BigDecimal("37.2410864"))
                .lng(new BigDecimal("127.1775537"))
                .imageUrl("image")
                .build();
        return restaurantRepository.save(restaurant);
    }

    private ParkingLot saveParkingLot(String name, String lat, String lng) {
        ParkingLot parkingLot = ParkingLot.builder()
                .parkingManagementNumber(unique("e2e-park-no"))
                .parkingLotName(name)
                .parkingLotDivision("E2E")
                .parkingLotType("노외")
                .roadAddress("경기도 용인시 처인구 테스트로 10")
                .lotAddress("경기도 용인시 처인구 테스트동 1")
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
        return parkingLotRepository.save(parkingLot);
    }

    private String unique(String prefix) {
        String normalizedPrefix = prefix.length() > 24 ? prefix.substring(0, 24) : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private void cleanupE2eData() {
        jdbcTemplate.update("""
                DELETE FROM parking_lots
                WHERE parking_lot_name LIKE 'e2e-parking-%'
                   OR parking_management_number LIKE 'e2e-park-no-%'
                   OR parking_management_number LIKE 'e2e-park-crud-%'
                   OR parking_management_number = 'TEST-PARK-1'
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurants
                WHERE name LIKE 'e2e-parking-restaurant-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM users
                WHERE provider_user_id LIKE 'provider-e2e-parking-%'
                   OR provider_user_id LIKE 'provider-e2e-parking-crud-%'
                   OR nickname LIKE 'nick-e2e-parking-%'
                   OR nickname LIKE 'nick-e2e-parking-crud-%'
                """);
    }
}
