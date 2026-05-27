package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.ApplyReservationMockResultRequest;
import com.example.Capstone.dto.request.ClawOpsRealCallPreflightRequest;
import com.example.Capstone.dto.response.ClawOpsRealCallPreflightResponse;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.example.Capstone.repository.UserRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "db", "key" })
class ReservationAdminE2ETest {

    private static final String SEED_TEST_PHONE_NUMBER = "+15550100001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantReservationRepository reservationRepository;

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
    @DisplayName("Mock 예약 결과 반영 API는 관리자 권한에서만 호출할 수 있다")
    void mockResultEndpointRequiresAdminRole() {
        User owner = saveUser("owner", User.Role.USER);
        User normalUser = saveUser("normal", User.Role.USER);
        User admin = saveUser("admin", User.Role.ADMIN);
        Restaurant restaurant = saveRestaurant();
        RestaurantReservation reservation = saveReservation(owner, restaurant);

        ApplyReservationMockResultRequest request = new ApplyReservationMockResultRequest(
                ReservationStatus.CONFIRMED,
                "Mock 예약 가능 확인",
                "예약이 확정되었습니다.",
                null,
                "e2e-admin-mock-" + reservation.getId()
        );

        ResponseEntity<ErrorResponse> userResponse = restTemplate.exchange(
                "/admin/reservations/{reservationId}/mock-result",
                HttpMethod.POST,
                authenticatedEntity(normalUser.getId(), User.Role.USER, request),
                ErrorResponse.class,
                reservation.getId()
        );

        assertEquals(HttpStatus.FORBIDDEN, userResponse.getStatusCode());

        ResponseEntity<ReservationResponse> adminResponse = restTemplate.exchange(
                "/admin/reservations/{reservationId}/mock-result",
                HttpMethod.POST,
                authenticatedEntity(admin.getId(), User.Role.ADMIN, request),
                ReservationResponse.class,
                reservation.getId()
        );

        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertNotNull(adminResponse.getBody());
        assertEquals(ReservationStatus.CONFIRMED, adminResponse.getBody().status());
        assertEquals("Mock 예약 가능 확인", adminResponse.getBody().aiSummary());
    }

    @Test
    @DisplayName("ClawOps 실제 발신 preflight API는 관리자 권한에서만 호출하고 실제 발신 없이 차단 사유를 반환한다")
    void clawOpsPreflightEndpointRequiresAdminRoleAndReturnsBlockedReasons() {
        User normalUser = saveUser("normal-preflight", User.Role.USER);
        User admin = saveUser("admin-preflight", User.Role.ADMIN);
        ClawOpsRealCallPreflightRequest request =
                new ClawOpsRealCallPreflightRequest(null, SEED_TEST_PHONE_NUMBER);

        ResponseEntity<ErrorResponse> userResponse = restTemplate.exchange(
                "/admin/reservations/clawops-real-call/preflight",
                HttpMethod.POST,
                authenticatedEntity(normalUser.getId(), User.Role.USER, request),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.FORBIDDEN, userResponse.getStatusCode());

        ResponseEntity<ClawOpsRealCallPreflightResponse> adminResponse = restTemplate.exchange(
                "/admin/reservations/clawops-real-call/preflight",
                HttpMethod.POST,
                authenticatedEntity(admin.getId(), User.Role.ADMIN, request),
                ClawOpsRealCallPreflightResponse.class
        );

        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertNotNull(adminResponse.getBody());
        assertFalse(adminResponse.getBody().realCallCandidate());
        assertEquals("****0001", adminResponse.getBody().targetPhoneNumberMasked());
        assertTrue(adminResponse.getBody().blockReasons().contains("CLAWOPS_MODE_REQUIRED"));
        assertTrue(adminResponse.getBody().blockReasons().contains("CALLING_DISABLED"));
        assertTrue(adminResponse.getBody().blockReasons().contains("REAL_CALL_DISABLED"));
    }

    private <T> HttpEntity<T> authenticatedEntity(Long userId, User.Role role, T body) {
        String token = jwtProvider.generateAccessToken(userId, role.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private User saveUser(String suffix, User.Role role) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(unique("provider-e2e-res-admin-" + suffix))
                .nickname(unique("nick-e2e-res-admin-" + suffix))
                .profileImageUrl("profile")
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Restaurant saveRestaurant() {
        Restaurant restaurant = Restaurant.builder()
                .name(unique("e2e-reservation-admin"))
                .address("경기도 용인시 처인구 예약테스트로 1")
                .roadAddress("경기도 용인시 처인구 예약테스트로 1")
                .categoryName("한식")
                .regionName("용인시")
                .lat(new BigDecimal("37.2410864"))
                .lng(new BigDecimal("127.1775537"))
                .imageUrl("image")
                .phoneNumber(SEED_TEST_PHONE_NUMBER)
                .build();
        return restaurantRepository.save(restaurant);
    }

    private RestaurantReservation saveReservation(User user, Restaurant restaurant) {
        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("e2e mock result")
                .restaurantPhoneNumberSnapshot(SEED_TEST_PHONE_NUMBER)
                .build();
        return reservationRepository.save(reservation);
    }

    private String unique(String prefix) {
        String normalizedPrefix = prefix.length() > 23 ? prefix.substring(0, 23) : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private void cleanupE2eData() {
        jdbcTemplate.update("""
                DELETE FROM restaurant_reservations
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE name LIKE 'e2e-reservation-admin-%'
                )
                   OR user_id IN (
                    SELECT id FROM users
                    WHERE provider_user_id LIKE 'provider-e2e-res-admin-%'
                       OR nickname LIKE 'nick-e2e-res-admin-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurants
                WHERE name LIKE 'e2e-reservation-admin-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM users
                WHERE provider_user_id LIKE 'provider-e2e-res-admin-%'
                   OR nickname LIKE 'nick-e2e-res-admin-%'
                """);
    }
}
