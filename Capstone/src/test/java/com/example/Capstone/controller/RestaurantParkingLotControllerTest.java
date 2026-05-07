package com.example.Capstone.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.exception.GlobalExceptionHandler;
import com.example.Capstone.service.ParkingLotService;

@ExtendWith(MockitoExtension.class)
class RestaurantParkingLotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ParkingLotService parkingLotService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantParkingLotController(parkingLotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("식당 기준 주차장 목록을 거리순 응답으로 반환한다")
    void getParkingLotsByDistanceReturnsRows() throws Exception {
        when(parkingLotService.getParkingLotsByDistance(eq(1L), eq(2), eq("공영")))
                .thenReturn(List.of(new ParkingLotResponse(
                        10L,
                        "용인중앙공영주차장",
                        "공영",
                        "노외",
                        "경기도 용인시 처인구 금령로 1",
                        "경기도 용인시 처인구 김량장동 1",
                        80,
                        "미시행",
                        "09:00~18:00",
                        "09:00~18:00",
                        "00:00~23:59",
                        new BigDecimal("37.2350000"),
                        new BigDecimal("127.2010000"),
                        30,
                        500,
                        10,
                        200,
                        "031-000-0000",
                        120
                )));

        mockMvc.perform(get("/restaurants/1/parking-lots")
                        .param("limit", "2")
                        .param("parkingLotDivision", "공영"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parkingLotName").value("용인중앙공영주차장"))
                .andExpect(jsonPath("$[0].parkingLotDivision").value("공영"))
                .andExpect(jsonPath("$[0].parkingCapacity").value(80))
                .andExpect(jsonPath("$[0].weekdayOperatingHours").value("09:00~18:00"))
                .andExpect(jsonPath("$[0].distanceMeters").value(120));
    }
}
