package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class RestaurantSeedPhoneNumberTest {

    private static final String RESERVATION_TEST_PHONE_NUMBER = "01000000000";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("기본 restaurant seed preview 전화번호는 예약 테스트용 번호로 통일되어 있다")
    void restaurantSeedPhoneNumbersAreUnifiedForReservationTesting() throws Exception {
        Path seedPath = Path.of("import-data", "restaurants-seed-preview.json");
        assumeTrue(Files.exists(seedPath), "Local import-data seed preview is not checked in.");

        List<Map<String, Object>> rows = objectMapper.readValue(
                seedPath.toFile(),
                new TypeReference<>() {}
        );

        assertThat(rows).isNotEmpty();
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.get("phone_number"))
                        .isEqualTo(RESERVATION_TEST_PHONE_NUMBER));
    }
}
