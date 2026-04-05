package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.service.RestaurantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant", description = "식당 API")
public class RestaurantController {

    private final RestaurantService restaurantService;

    @Operation(summary = "식당 검색")
    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> searchRestaurants(
            @RequestParam String keyword) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(keyword));
    }

    @Operation(summary = "식당 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getRestaurant(
            @PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getRestaurant(id));
    }
}
