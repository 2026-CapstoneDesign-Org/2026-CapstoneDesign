package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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

import com.example.Capstone.client.reservation.ClawOpsSidecarInternalSignature;
import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.ReservationProviderWebhookResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.repository.ReservationProviderEventRepository;
import com.example.Capstone.repository.RestaurantRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.example.Capstone.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "clawops.sidecar-internal-signing-key=fake-sidecar-signing-key"
)
@ActiveProfiles({ "db", "key" })
class ClawOpsAgentProviderEventE2ETest {

    private static final String TEST_SECRET = "fake-sidecar-signing-key";
    private static final String PLACEHOLDER_PHONE_NUMBER = "+15550100001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantReservationRepository reservationRepository;

    @Autowired
    private ReservationProviderEventRepository providerEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanBeforeEach() {
        cleanupE2eData();
    }

    @AfterEach
    void cleanAfterEach() {
        cleanupE2eData();
    }

    @Test
    @DisplayName("ClawOps agent internal CALL_QUEUED 이벤트는 ledger에만 기록하고 예약 상태를 오염시키지 않는다")
    void clawOpsAgentQueuedEventRecordsLedgerOnly() {
        RestaurantReservation reservation = saveReservation("queued");
        String rawPayload = queuedPayload(reservation, "dry-run-event-1");

        ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                signedEntity(rawPayload, Instant.now()),
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ReservationProviderEventProcessingStatus.PROCESSED, response.getBody().processingStatus());
        assertEquals(ReservationStatus.REQUESTED, response.getBody().reservationStatus());

        RestaurantReservation updatedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.REQUESTED, updatedReservation.getStatus());
        assertNull(updatedReservation.getProvider());
        assertNull(updatedReservation.getProviderCallId());
        assertEquals(
                ReservationProviderEventType.CALL_QUEUED,
                providerEventRepository.findByIdempotencyKey("CLAWOPS_SIDECAR:dry-run-event-1")
                        .orElseThrow()
                        .getEventType()
        );
    }

    @Test
    @DisplayName("같은 ClawOps agent idempotency key는 중복 반영하지 않는다")
    void clawOpsAgentDuplicateEventIsIgnored() {
        RestaurantReservation reservation = saveReservation("duplicate");
        String rawPayload = queuedPayload(reservation, "dry-run-event-duplicate");
        HttpEntity<String> requestEntity = signedEntity(rawPayload, Instant.now());

        restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                requestEntity,
                ReservationProviderWebhookResponse.class
        );
        ResponseEntity<ReservationProviderWebhookResponse> duplicateResponse = restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                requestEntity,
                ReservationProviderWebhookResponse.class
        );

        assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode());
        assertNotNull(duplicateResponse.getBody());
        assertEquals(
                ReservationProviderEventProcessingStatus.IGNORED_DUPLICATE,
                duplicateResponse.getBody().processingStatus()
        );
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("signature가 잘못된 ClawOps agent internal event는 401로 거부한다")
    void clawOpsAgentEventRejectsInvalidSignature() {
        RestaurantReservation reservation = saveReservation("bad-signature");
        String rawPayload = queuedPayload(reservation, "dry-run-event-bad-signature");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER, Instant.now().toString());
        headers.add(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER, "bad-signature");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                new HttpEntity<>(rawPayload, headers),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("timestamp가 오래된 ClawOps agent internal event는 401로 거부한다")
    void clawOpsAgentEventRejectsStaleTimestamp() {
        RestaurantReservation reservation = saveReservation("stale-timestamp");
        String rawPayload = queuedPayload(reservation, "dry-run-event-stale");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                signedEntity(rawPayload, Instant.now().minus(Duration.ofMinutes(10))),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ReservationStatus.REQUESTED, reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus());
    }

    @Test
    @DisplayName("sidecar Spring event payload fixture는 Spring internal DTO와 event ledger 흐름에 호환된다")
    void sidecarSpringEventPayloadFixturesAreAcceptedBySpringEndpoint() throws Exception {
        Map<String, ExpectedFixtureResult> fixtures = new LinkedHashMap<>();
        fixtures.put(
                "confirmed.json",
                new ExpectedFixtureResult(ReservationProviderEventType.RESERVATION_CONFIRMED, ReservationStatus.CONFIRMED)
        );
        fixtures.put(
                "unavailable.json",
                new ExpectedFixtureResult(
                        ReservationProviderEventType.RESERVATION_UNAVAILABLE,
                        ReservationStatus.UNAVAILABLE
                )
        );
        fixtures.put(
                "needs-confirmation.json",
                new ExpectedFixtureResult(
                        ReservationProviderEventType.RESERVATION_NEEDS_CONFIRMATION,
                        ReservationStatus.NEEDS_CONFIRMATION
                )
        );
        fixtures.put(
                "failed.json",
                new ExpectedFixtureResult(ReservationProviderEventType.CALL_CONNECTION_FAILED, ReservationStatus.CALLING)
        );
        fixtures.put(
                "ai-parse-failed.json",
                new ExpectedFixtureResult(ReservationProviderEventType.AI_PARSE_FAILED, ReservationStatus.NEEDS_CONFIRMATION)
        );

        for (Map.Entry<String, ExpectedFixtureResult> fixture : fixtures.entrySet()) {
            RestaurantReservation reservation = saveReservation("fixture-" + fixture.getKey().replace(".json", ""));
            String rawPayload = springEventFixturePayload(fixture.getKey(), reservation.getId());

            ResponseEntity<ReservationProviderWebhookResponse> response = restTemplate.postForEntity(
                    "/internal/reservations/provider-events/clawops-agent",
                    signedEntity(rawPayload, Instant.now()),
                    ReservationProviderWebhookResponse.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), fixture.getKey());
            assertNotNull(response.getBody(), fixture.getKey());
            assertEquals(
                    ReservationProviderEventProcessingStatus.PROCESSED,
                    response.getBody().processingStatus(),
                    fixture.getKey()
            );
            assertEquals(fixture.getValue().reservationStatus(), response.getBody().reservationStatus(), fixture.getKey());

            JsonNode payloadNode = objectMapper.readTree(rawPayload);
            String idempotencyKey = "CLAWOPS_SIDECAR:" + payloadNode.get("idempotencyKey").asText();
            assertEquals(
                    fixture.getValue().eventType(),
                    providerEventRepository.findByIdempotencyKey(idempotencyKey)
                            .orElseThrow()
                            .getEventType(),
                    fixture.getKey()
            );
            assertEquals(
                    fixture.getValue().reservationStatus(),
                    reservationRepository.findById(reservation.getId()).orElseThrow().getStatus(),
                    fixture.getKey()
            );
        }
    }

    @Test
    @DisplayName("sidecar Spring event payload fixture도 idempotency key 중복이면 중복 반영하지 않는다")
    void sidecarSpringEventPayloadFixtureDuplicateIsIgnored() throws Exception {
        RestaurantReservation reservation = saveReservation("fixture-duplicate");
        String rawPayload = springEventFixturePayload("confirmed.json", reservation.getId());
        HttpEntity<String> requestEntity = signedEntity(rawPayload, Instant.now());

        restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
                requestEntity,
                ReservationProviderWebhookResponse.class
        );
        ResponseEntity<ReservationProviderWebhookResponse> duplicateResponse = restTemplate.postForEntity(
                "/internal/reservations/provider-events/clawops-agent",
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

    private HttpEntity<String> signedEntity(String rawPayload, Instant timestamp) {
        String timestampValue = timestamp.toString();
        String signature = ClawOpsSidecarInternalSignature.sign(TEST_SECRET, timestampValue, rawPayload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER, timestampValue);
        headers.add(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER, signature);
        return new HttpEntity<>(rawPayload, headers);
    }

    private String queuedPayload(RestaurantReservation reservation, String idempotencyKey) {
        return """
                {
                  "provider": "CLAWOPS_SIDECAR",
                  "reservationId": %d,
                  "sidecarCallId": "dry-run-sidecar-%d",
                  "eventType": "CALL_QUEUED",
                  "providerStatus": "DRY_RUN_QUEUED",
                  "occurredAt": "2026-05-26T12:00:00",
                  "retryable": false,
                  "resultMessage": "dry-run queued",
                  "idempotencyKey": "%s",
                  "rawPayloadHash": "dry-run-hash-%s"
                }
                """.formatted(reservation.getId(), reservation.getId(), idempotencyKey, idempotencyKey);
    }

    private String springEventFixturePayload(String fixtureName, Long reservationId) throws IOException {
        Path fixturePath = Path.of(
                "..",
                "sidecars",
                "clawops-voice-agent",
                "contracts",
                "spring-event-payloads",
                fixtureName
        );
        ObjectNode payload = (ObjectNode) objectMapper.readTree(Files.readString(fixturePath));
        payload.put("reservationId", reservationId);
        return objectMapper.writeValueAsString(payload);
    }

    private RestaurantReservation saveReservation(String suffix) {
        User user = saveUser(suffix);
        Restaurant restaurant = saveRestaurant(suffix);
        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("e2e clawops agent")
                .restaurantPhoneNumberSnapshot(PLACEHOLDER_PHONE_NUMBER)
                .build();
        return reservationRepository.save(reservation);
    }

    private User saveUser(String suffix) {
        User user = User.builder()
                .provider("kakao")
                .providerUserId(unique("provider-e2e-agent-" + suffix))
                .nickname(unique("nick-e2e-agent-" + suffix))
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }

    private Restaurant saveRestaurant(String suffix) {
        Restaurant restaurant = Restaurant.builder()
                .name(unique("e2e-clawops-agent-" + suffix))
                .address("경기도 용인시 처인구 내부이벤트로 1")
                .roadAddress("경기도 용인시 처인구 내부이벤트로 1")
                .categoryName("한식")
                .regionName("용인시")
                .lat(new BigDecimal("37.2410864"))
                .lng(new BigDecimal("127.1775537"))
                .imageUrl("image")
                .phoneNumber(PLACEHOLDER_PHONE_NUMBER)
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
                    WHERE r.name LIKE 'e2e-clawops-agent-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM reservation_provider_events
                WHERE reservation_id IN (
                    SELECT rr.id
                    FROM restaurant_reservations rr
                    JOIN restaurants r ON rr.restaurant_id = r.id
                    WHERE r.name LIKE 'e2e-clawops-agent-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurant_reservations
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE name LIKE 'e2e-clawops-agent-%'
                )
                   OR user_id IN (
                    SELECT id FROM users
                    WHERE provider_user_id LIKE 'provider-e2e-agent-%'
                       OR nickname LIKE 'nick-e2e-agent-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM restaurants
                WHERE name LIKE 'e2e-clawops-agent-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM users
                WHERE provider_user_id LIKE 'provider-e2e-agent-%'
                   OR nickname LIKE 'nick-e2e-agent-%'
                """);
    }

    private record ExpectedFixtureResult(
            ReservationProviderEventType eventType,
            ReservationStatus reservationStatus
    ) {
    }
}
