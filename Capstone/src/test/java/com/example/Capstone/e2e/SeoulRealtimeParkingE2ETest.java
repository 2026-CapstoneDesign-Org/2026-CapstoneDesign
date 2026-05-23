package com.example.Capstone.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import com.example.Capstone.common.jwt.JwtProvider;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.repository.UserRepository;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "parking-lot.seoul-citydata.enabled=true",
                "gemini.api.url=http://localhost",
                "gemini.api.key=test",
                "aws.s3.region=ap-northeast-2",
                "aws.s3.access-key=test",
                "aws.s3.secret-key=test",
                "aws.s3.bucket=test",
                "aws.s3.presigned-url-expiration=3600"
        }
)
@EnabledIfEnvironmentVariable(named = "SEOUL_CITYDATA_API_KEY", matches = ".+")
class SeoulRealtimeParkingE2ETest {

    private static final String GWANGHWAMUN_LAT = "37.57340269";
    private static final String GWANGHWAMUN_LNG = "126.97588429";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanAfterEach() {
        jdbcTemplate.update("""
                DELETE FROM users
                WHERE provider_user_id LIKE 'provider-e2e-seoul-parking-%'
                   OR nickname LIKE 'nick-e2e-seoul-parking-%'
                """);
    }

    @Test
    @DisplayName("coordinate lookup returns Seoul realtime parking counts")
    void returnsSeoulRealtimeParkingCounts() {
        User user = saveUser();

        ResponseEntity<ParkingLotResponse[]> response = restTemplate.exchange(
                "/parking-lots/nearby?lat={lat}&lng={lng}&limit=10",
                HttpMethod.GET,
                authenticatedEntity(user.getId()),
                ParkingLotResponse[].class,
                GWANGHWAMUN_LAT,
                GWANGHWAMUN_LNG
        );

        assertTrue(response.getStatusCode().isSameCodeAs(HttpStatus.OK));
        assertNotNull(response.getBody());

        boolean hasRealtimeCount = Arrays.stream(response.getBody())
                .anyMatch(parkingLot -> "SEOUL_CITYDATA".equals(parkingLot.realtimeSource())
                        && parkingLot.currentParkingCount() != null
                        && parkingLot.currentParkingTime() != null);

        assertTrue(hasRealtimeCount);
    }

    private HttpEntity<Void> authenticatedEntity(Long userId) {
        String token = jwtProvider.generateAccessToken(userId, User.Role.USER.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User user = User.builder()
                .provider("kakao")
                .providerUserId("provider-e2e-seoul-parking-" + suffix)
                .nickname("nick-e2e-seoul-parking-" + suffix)
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }
}
