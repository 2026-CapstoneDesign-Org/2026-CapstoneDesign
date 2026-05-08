package com.example.Capstone.controller;

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

import com.example.Capstone.dto.response.RestaurantBusinessHoursResponse;
import com.example.Capstone.dto.response.RestaurantBusinessHoursDisplayResponse;
import com.example.Capstone.dto.response.RestaurantCurrentBusinessStatusResponse;
import com.example.Capstone.dto.response.RestaurantDetailResponse;
import com.example.Capstone.dto.response.RestaurantMenuItemResponse;
import com.example.Capstone.dto.response.RestaurantPhotoResponse;
import com.example.Capstone.dto.response.RestaurantTagResponse;
import com.example.Capstone.exception.GlobalExceptionHandler;
import com.example.Capstone.service.RestaurantService;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RestaurantService restaurantService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("restaurant detail returns home and menu fields")
    void getRestaurantReturnsDetailPageFields() throws Exception {
        RestaurantBusinessHoursResponse businessHours = new RestaurantBusinessHoursResponse(
                "pcmap_business_hours",
                List.of(new RestaurantBusinessHoursResponse.DayHours(
                        "Tue",
                        new RestaurantBusinessHoursResponse.TimeRange("11:00", "21:00"),
                        List.of(),
                        List.of(),
                        null,
                        false
                )),
                List.of(),
                null,
                null
        );
        RestaurantCurrentBusinessStatusResponse currentBusinessStatus =
                new RestaurantCurrentBusinessStatusResponse(
                        "OPEN",
                        "영업 중",
                        true,
                        "2026-05-05T12:00:00+09:00",
                        "화",
                        "12:00",
                        "21:00에 영업 종료"
                );
        RestaurantBusinessHoursDisplayResponse businessHoursDisplay =
                new RestaurantBusinessHoursDisplayResponse(
                        "영업 중 · 21:00에 영업 종료",
                        "Tue 11:00 - 21:00",
                        List.of(new RestaurantBusinessHoursDisplayResponse.Row(
                                "Tue",
                                "11:00 - 21:00",
                                List.of(),
                                true,
                                false
                        )),
                        null
                );

        when(restaurantService.getRestaurant(1L))
                .thenReturn(new RestaurantDetailResponse(
                        1L,
                        "Sample Restaurant",
                        "Road address",
                        "Road address",
                        "Lot address",
                        "Sample Region",
                        new BigDecimal("37.2320262"),
                        new BigDecimal("127.1868096"),
                        "https://example.com/restaurant.jpg",
                        List.of(new RestaurantPhotoResponse(
                                "https://example.com/restaurant.jpg",
                                "RESTAURANT_IMAGE",
                                0
                        )),
                        "0507-1494-0341",
                        businessHours,
                        businessHoursDisplay,
                        currentBusinessStatus,
                        "Chinese",
                        "Chinese",
                        List.of("Chinese"),
                        List.of(new RestaurantTagResponse(
                                10L,
                                "menu:\uD0D5\uC218\uC721",
                                "\uD0D5\uC218\uC721",
                                null,
                                true,
                                1
                        )),
                        List.of(new RestaurantMenuItemResponse(
                                100L,
                                0,
                                "Jajangmyeon",
                                "Black bean noodles",
                                "7,500",
                                new BigDecimal("7500")
                        ))
                ));

        mockMvc.perform(get("/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sample Restaurant"))
                .andExpect(jsonPath("$.address").value("Road address"))
                .andExpect(jsonPath("$.phoneNumber").value("0507-1494-0341"))
                .andExpect(jsonPath("$.businessHours.days[0].businessHours.start").value("11:00"))
                .andExpect(jsonPath("$.businessHoursDisplay.statusLine").value("영업 중 · 21:00에 영업 종료"))
                .andExpect(jsonPath("$.businessHoursDisplay.rows[0].timeText").value("11:00 - 21:00"))
                .andExpect(jsonPath("$.currentBusinessStatus.status").value("OPEN"))
                .andExpect(jsonPath("$.currentBusinessStatus.isOpen").value(true))
                .andExpect(jsonPath("$.primaryCategoryName").value("Chinese"))
                .andExpect(jsonPath("$.photos[0].imageUrl").value("https://example.com/restaurant.jpg"))
                .andExpect(jsonPath("$.additionalInfoTags[0].tagName").value("\uD0D5\uC218\uC721"))
                .andExpect(jsonPath("$.menus[0].menuName").value("Jajangmyeon"))
                .andExpect(jsonPath("$.menus[0].priceText").value("7,500"));
    }
}
