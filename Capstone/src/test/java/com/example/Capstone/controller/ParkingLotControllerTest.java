package com.example.Capstone.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.Capstone.dto.request.CreateParkingLotRequest;
import com.example.Capstone.dto.request.UpdateParkingLotRequest;
import com.example.Capstone.dto.response.ParkingLotResponse;
import com.example.Capstone.exception.GlobalExceptionHandler;
import com.example.Capstone.service.ParkingLotService;

@ExtendWith(MockitoExtension.class)
class ParkingLotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ParkingLotService parkingLotService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ParkingLotController(parkingLotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("주차장 목록을 조회한다")
    void listsParkingLots() throws Exception {
        when(parkingLotService.getParkingLots("공영", 10))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/parking-lots")
                        .param("parkingLotDivision", "공영")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parkingLotName").value("테스트 공영주차장"));
    }

    @Test
    @DisplayName("주차장 단건을 조회한다")
    void getsParkingLot() throws Exception {
        when(parkingLotService.getParkingLot(1L))
                .thenReturn(response());

        mockMvc.perform(get("/parking-lots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkingLotName").value("테스트 공영주차장"));
    }

    @Test
    @DisplayName("주차장을 생성한다")
    void createsParkingLot() throws Exception {
        when(parkingLotService.createParkingLot(any(CreateParkingLotRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/parking-lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingManagementNumber": "CRUD-1",
                                  "parkingLotName": "테스트 공영주차장",
                                  "parkingLotDivision": "공영",
                                  "parkingLotType": "노외",
                                  "lat": 37.2410864,
                                  "lng": 127.1775537
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parkingLotName").value("테스트 공영주차장"));
    }

    @Test
    @DisplayName("주차장을 수정한다")
    void updatesParkingLot() throws Exception {
        when(parkingLotService.updateParkingLot(eq(1L), any(UpdateParkingLotRequest.class)))
                .thenReturn(response());

        mockMvc.perform(patch("/parking-lots/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parkingLotName": "테스트 공영주차장",
                                  "parkingLotDivision": "공영",
                                  "parkingLotType": "노외"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkingLotName").value("테스트 공영주차장"));
    }

    @Test
    @DisplayName("주차장을 삭제한다")
    void deletesParkingLot() throws Exception {
        doNothing().when(parkingLotService).deleteParkingLot(1L);

        mockMvc.perform(delete("/parking-lots/1"))
                .andExpect(status().isNoContent());
    }

    private ParkingLotResponse response() {
        return new ParkingLotResponse(
                1L,
                "테스트 공영주차장",
                "공영",
                "노외",
                "경기도 용인시 처인구 테스트로 10",
                "경기도 용인시 처인구 테스트동 1",
                50,
                "미시행",
                "00:00~23:59",
                "00:00~23:59",
                "00:00~23:59",
                new BigDecimal("37.2410864"),
                new BigDecimal("127.1775537"),
                30,
                500,
                10,
                200,
                "031-000-0000",
                null
        );
    }
}
