package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.response.HiddenGemRestaurantResponse;
import com.example.Capstone.dto.response.ListRecommendationResponse;
import com.example.Capstone.dto.response.RestaurantRecommendationResponse;
import com.example.Capstone.service.HiddenGemRecommendationService;
import com.example.Capstone.service.ListRecommendationService;
import com.example.Capstone.service.RestaurantRecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendation", description = "추천 API")
public class RecommendationController {

    private final ListRecommendationService listRecommendationService;
    private final RestaurantRecommendationService restaurantRecommendationService;
    private final HiddenGemRecommendationService hiddenGemRecommendationService;

    @Operation(summary = "리스트 추천 조회")
    @GetMapping("/lists")
    public ResponseEntity<ListRecommendationResponse> getListRecommendations(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(listRecommendationService.getListRecommendations(userId));
    }

    @Operation(summary = "식당 추천 조회")
    @GetMapping("/restaurants")
    public ResponseEntity<RestaurantRecommendationResponse> getRestaurantRecommendations(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(restaurantRecommendationService.getRestaurantRecommendations(userId));
    }

    @Operation(summary = "숨은 맛집 추천 조회")
    @GetMapping("/restaurants/hidden-gems")
    public ResponseEntity<HiddenGemRestaurantResponse> getHiddenGemRestaurants(
            @RequestParam(required = false) String regionTownName) {
        return ResponseEntity.ok(hiddenGemRecommendationService.getHiddenGemRestaurants(regionTownName));
    }
}
