package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.Capstone.client.reservation.MockReservationProviderWebhookSecurityVerifier;
import com.example.Capstone.domain.ReservationCallAttemptStatus;
import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.ReservationProviderWebhookResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.ReservationCallAttemptRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.example.Capstone.repository.UserRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "db", "key" })
class ReservationProviderWebhookE2ETest {

    private static final String TEST_SECRET = "test-mock-reservation-webhook-secret";
    private static final String TEST_PHONE_NUMBER = "+15550100001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantReservationRepository reservationRepository;

    @Autowired
    private ReservationCallAttemptRepository callAttemptRepository;

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
    @DisplayName("ClawOps inbound webhook은 인증 없이 GET 요청에 Voice XML을 반환한다")
    void clawOpsInboundWebhookReturnsVoiceXmlForGet() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/webhooks/reservations/call-providers/clawops/inbound",
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(MediaType.TEXT_XML, response.getHeaders().getContentType());
        assertEquals(true, response.getBody().contains("<Response>"));
        assertEquals(true, response.getBody().contains("<Say language=\"ko-KR\">"));
        assertEquals(true, response.getBody().contains("<Hangup/>"));
    }

    @Test
    @DisplayName("ClawOps inbound webhook은 인증 없이 POST 요청에 Voice XML을 반환한다")
    void clawOpsInboundWebhookReturnsVoiceXmlForPost() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/clawops/inbound",
                new HttpEntity<>(""),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(MediaType.TEXT_XML, response.getHeaders().getContentType());
        assertEquals(true, response.getBody().contains("<Response>"));
        assertEquals(true, response.getBody().contains("<Say language=\"ko-KR\">"));
        assertEquals(true, response.getBody().contains("<Hangup/>"));
    }

    @Test
    @DisplayName("정상 mock provider webhook 요청은 내부 event 처리를 거쳐 예약 상태에 반영된다")
    void mockWebhookConfirmsReservation() {
        RestaurantReservation reservation = saveReservation("confirm");
        String rawPayload = confirmedPayload(reservation, "webhook-event-1");

        ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                signedEntity(rawPayload, Instant.now()),
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ReservationProviderEventProcessingStatus.PROCESSED, response.getBody().processingStatus());
        assertEquals(ReservationStatus.CONFIRMED, response.getBody().reservationStatus());

        RestaurantReservation updatedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, updatedReservation.getStatus());
        assertEquals("Mock webhook 예약 가능 확인", updatedReservation.getAiSummary());
        assertEquals(1, updatedReservation.getAttemptCount());
        assertEquals(ReservationCallAttemptStatus.COMPLETED, callAttemptRepository
                .findByReservationIdAndProviderAndProviderCallId(
                        reservation.getId(),
                        "MOCK",
                        "mock-" + reservation.getId()
                )
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("같은 mock provider webhook 이벤트는 중복 반영하지 않는다")
    void mockWebhookIgnoresDuplicateEvent() {
        RestaurantReservation reservation = saveReservation("duplicate");
        String rawPayload = confirmedPayload(reservation, "webhook-event-duplicate");
        HttpEntity<String> requestEntity = signedEntity(rawPayload, Instant.now());

        restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                requestEntity,
                ReservationProviderWebhookResponse.class
        );
        ResponseEntity<ReservationProviderWebhookResponse> duplicateResponse = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                requestEntity,
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode());
        assertNotNull(duplicateResponse.getBody());
        assertEquals(
                ReservationProviderEventProcessingStatus.IGNORED_DUPLICATE,
                duplicateResponse.getBody().processingStatus()
        );
        assertEquals(ReservationStatus.CONFIRMED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("signature가 잘못된 mock webhook 요청은 예약 상태를 변경하지 않는다")
    void mockWebhookRejectsInvalidSignature() {
        RestaurantReservation reservation = saveReservation("bad-signature");
        String rawPayload = confirmedPayload(reservation, "webhook-event-bad-signature");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(MockReservationProviderWebhookSecurityVerifier.TIMESTAMP_HEADER, Instant.now().toString());
        headers.add(MockReservationProviderWebhookSecurityVerifier.SIGNATURE_HEADER, "bad-signature");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                new HttpEntity<>(rawPayload, headers),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("timestamp가 오래된 mock webhook 요청은 예약 상태를 변경하지 않는다")
    void mockWebhookRejectsStaleTimestamp() {
        RestaurantReservation reservation = saveReservation("stale-timestamp");
        String rawPayload = confirmedPayload(reservation, "webhook-event-stale");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                signedEntity(rawPayload, Instant.now().minus(Duration.ofMinutes(10))),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("잘못된 provider call id는 event ledger에 거부로 남기고 예약 상태를 변경하지 않는다")
    void mockWebhookRejectsMismatchedProviderCallId() {
        RestaurantReservation reservation = saveReservation("wrong-call");
        String rawPayload = """
                {
                  "reservationId": %d,
                  "providerCallId": "wrong-call-id",
                  "providerEventId": "webhook-event-wrong-call",
                  "eventType": "RESERVATION_CONFIRMED",
                  "providerStatus": "MOCK_CONFIRMED",
                  "occurredAt": "2026-05-20T12:00:00",
                  "aiSummary": "잘못된 call id",
                  "resultMessage": "반영되면 안 됩니다."
                }
                """.formatted(reservation.getId());

        ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                signedEntity(rawPayload, Instant.now()),
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ReservationProviderEventProcessingStatus.REJECTED_INVALID, response.getBody().processingStatus());
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("존재하지 않는 예약의 mock webhook 이벤트는 거부 상태로 처리한다")
    void mockWebhookRejectsMissingReservation() {
        String rawPayload = """
                {
                  "reservationId": 987654321,
                  "providerCallId": "mock-987654321",
                  "providerEventId": "webhook-event-missing-reservation",
                  "eventType": "RESERVATION_CONFIRMED",
                  "providerStatus": "MOCK_CONFIRMED",
                  "occurredAt": "2026-05-20T12:00:00",
                  "aiSummary": "존재하지 않는 예약",
                  "resultMessage": "반영되면 안 됩니다."
                }
                """;

        ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                signedEntity(rawPayload, Instant.now()),
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ReservationProviderEventProcessingStatus.REJECTED_INVALID, response.getBody().processingStatus());
    }

    @Test
    @DisplayName("terminal 상태 예약에 늦게 도착한 mock webhook 이벤트는 상태를 덮어쓰지 않는다")
    void mockWebhookIgnoresLateEventAfterTerminalStatus() {
        RestaurantReservation reservation = saveReservation("late-event");
        reservation.applyMockResult(
                ReservationStatus.CONFIRMED,
                "이미 확정",
                "이미 예약이 확정되었습니다.",
                null,
                "mock-" + reservation.getId()
        );
        reservationRepository.save(reservation);

        String rawPayload = """
                {
                  "reservationId": %d,
                  "providerCallId": "mock-%d",
                  "providerEventId": "webhook-event-late-started",
                  "eventType": "CALL_STARTED",
                  "providerStatus": "MOCK_CALL_STARTED",
                  "occurredAt": "2026-05-20T12:00:00"
                }
                """.formatted(reservation.getId(), reservation.getId());

        ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                "/webhooks/reservations/call-providers/mock",
                signedEntity(rawPayload, Instant.now()),
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ReservationProviderEventProcessingStatus.IGNORED_STALE, response.getBody().processingStatus());
        assertEquals(ReservationStatus.CONFIRMED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    private HttpEntity<String> signedEntity(String rawPayload, Instant timestamp) {
        String timestampValue = timestamp.toString();
        String signature = MockReservationProviderWebhookSecurityVerifier.sign(
                TEST_SECRET,
                timestampValue,
                rawPayload
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(MockReservationProviderWebhookSecurityVerifier.TIMESTAMP_HEADER, timestampValue);
        headers.add(MockReservationProviderWebhookSecurityVerifier.SIGNATURE_HEADER, signature);
        return new HttpEntity<>(rawPayload, headers);
    }

    private String confirmedPayload(RestaurantReservation reservation, String providerEventId) {
        return """
                {
                  "reservationId": %d,
                  "providerCallId": "mock-%d",
                  "providerEventId": "%s",
                  "eventType": "RESERVATION_CONFIRMED",
                  "providerStatus": "MOCK_CONFIRMED",
                  "occurredAt": "2026-05-20T12:00:00",
                  "aiSummary": "Mock webhook 예약 가능 확인",
                  "resultMessage": "예약이 확정되었습니다."
                }
                """.formatted(reservation.getId(), reservation.getId(), providerEventId);
    }

    private RestaurantReservation saveReservation(String suffix) {
        User user = saveUser(suffix);
        Restaurant restaurant = saveRestaurant(suffix);
        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("e2e webhook")
                .restaurantPhoneNumberSnapshot(TEST_PHONE_NUMBER)
                .build();
        RestaurantReservation savedReservation = reservationRepository.save(reservation);
        savedReservation.applyProviderStart(
                ReservationStatus.REQUESTED,
                "MOCK",
                "mock-" + savedReservation.getId(),
                "MOCK_WAITING_RESULT"
        );
        return reservationRepository.save(savedReservation);
    }

    private User saveUser(String suffix) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(unique("provider-e2e-webhook-" + suffix))
                .nickname(unique("nick-e2e-webhook-" + suffix))
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }

    private Restaurant saveRestaurant(String suffix) {
        Restaurant restaurant = Restaurant.builder()
                .name(unique("e2e-reservation-webhook-" + suffix))
                .address("경기도 용인시 처인구 웹훅테스트로 1")
                .roadAddress("경기도 용인시 처인구 웹훅테스트로 1")
                .categoryName("한식")
                .regionName("용인시")
                .lat(new BigDecimal("37.2410864"))
                .lng(new BigDecimal("127.1775537"))
                .imageUrl("image")
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();
        return restaurantRepository.save(restaurant);
    }

    private String unique(String prefix) {
        String normalizedPrefix = prefix.length() > 23 ? prefix.substring(0, 23) : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private void cleanupE2eData() {
        jdbcTemplate.update("""
                DELETE FROM reservation_call_attempts
                WHERE reservation_id IN (
                    SELECT rr.id
                    FROM restaurant_reservations rr
                    JOIN restaurants r ON rr.restaurant_id = r.id
                    WHERE r.name LIKE 'e2e-reservation-webhook-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM reservation_provider_events
                WHERE reservation_id IN (
                    SELECT rr.id
                    FROM restaurant_reservations rr
                    JOIN restaurants r ON rr.restaurant_id = r.id
                    WHERE r.name LIKE 'e2e-reservation-webhook-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurant_reservations
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE name LIKE 'e2e-reservation-webhook-%'
                )
                   OR user_id IN (
                    SELECT id FROM users
                    WHERE provider_user_id LIKE 'provider-e2e-webhook-%'
                       OR nickname LIKE 'nick-e2e-webhook-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurants
                WHERE name LIKE 'e2e-reservation-webhook-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM users
                WHERE provider_user_id LIKE 'provider-e2e-webhook-%'
                   OR nickname LIKE 'nick-e2e-webhook-%'
                """);
    }
}
