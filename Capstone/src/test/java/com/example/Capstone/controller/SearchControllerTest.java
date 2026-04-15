package com.example.Capstone.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.Capstone.dto.response.SearchInterpretationResponse;
import com.example.Capstone.dto.response.SearchRegionItemResponse;
import com.example.Capstone.dto.response.SearchResponse;
import com.example.Capstone.dto.response.SearchRestaurantItemResponse;
import com.example.Capstone.dto.response.SearchUserItemResponse;
import com.example.Capstone.exception.GlobalExceptionHandler;
import com.example.Capstone.service.SearchService;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("통합 검색 응답 구조를 그대로 반환한다")
    void returnsIntegratedSearchResponse() throws Exception {
        when(searchService.search("성수 냉면"))
                .thenReturn(new SearchResponse(
                        "성수 냉면",
                        "RESTAURANT",
                        new SearchInterpretationResponse("성수 냉면", "성수 냉면", false, false, null, "성수", "냉면", false, false),
                        1,
                        1,
                        1,
                        List.of(new SearchRestaurantItemResponse(
                                1L,
                                "place-1",
                                "INTERNAL",
                                "성수 냉면집",
                                "성수",
                                "서울 성수동",
                                null,
                                null,
                                "image",
                                List.of("한식"),
                                "NAME_CONTAINS"
                        )),
                        List.of(new SearchUserItemResponse(2L, "성수매니아", "profile")),
                        List.of(new SearchRegionItemResponse("성수", "성수", "성수", "/rankings/restaurants?regionName=%EC%84%B1%EC%88%98"))
                ));

        mockMvc.perform(get("/search").param("query", "성수 냉면"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryType").value("RESTAURANT"))
                .andExpect(jsonPath("$.restaurantCount").value(1))
                .andExpect(jsonPath("$.userCount").value(1))
                .andExpect(jsonPath("$.regionCount").value(1))
                .andExpect(jsonPath("$.interpretation.rawQuery").value("성수 냉면"))
                .andExpect(jsonPath("$.interpretation.regionKeyword").value("성수"))
                .andExpect(jsonPath("$.interpretation.restaurantKeyword").value("냉면"))
                .andExpect(jsonPath("$.restaurants[0].restaurantId").value(1))
                .andExpect(jsonPath("$.restaurants[0].source").value("INTERNAL"))
                .andExpect(jsonPath("$.users[0].nickname").value("성수매니아"))
                .andExpect(jsonPath("$.regions[0].regionName").value("성수"))
                .andExpect(jsonPath("$.regions[0].regionKeyword").value("성수"));
    }
}
