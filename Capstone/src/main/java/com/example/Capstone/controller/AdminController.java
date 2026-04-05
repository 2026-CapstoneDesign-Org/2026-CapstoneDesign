package com.example.Capstone.controller;

import com.example.Capstone.dto.request.*;
import com.example.Capstone.dto.response.RestaurantResponse;
import com.example.Capstone.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "관리자 API")
@PreAuthorize("hasRole('ADMIN')")       // ← ADMIN 권한만 접근 가능
public class AdminController {

    private final AdminService adminService;

    // 식당 등록
    @Operation(summary = "식당 등록")
    @PostMapping("/restaurants")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @RequestBody @Valid CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createRestaurant(request));
    }

    // 식당 정보 수정
    @Operation(summary = "식당 정보 수정")
    @PatchMapping("/restaurants/{id}")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable Long id,
            @RequestBody @Valid UpdateRestaurantRequest request) {
        return ResponseEntity.ok(adminService.updateRestaurant(id, request));
    }

    // 카테고리 수정
    @Operation(summary = "카테고리 수정")
    @PatchMapping("/restaurants/{id}/categories")
    public ResponseEntity<Void> updateCategories(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCategoryRequest request) {
        adminService.updateCategories(id, request);
        return ResponseEntity.ok().build();
    }

    // 식당 비노출
    @Operation(summary = "식당 비노출")
    @PatchMapping("/restaurants/{id}/hide")
    public ResponseEntity<Void> hideRestaurant(@PathVariable Long id) {
        adminService.hideRestaurant(id);
        return ResponseEntity.ok().build();
    }

    // 유저 비노출
    @Operation(summary = "유저 비노출")
    @PatchMapping("/users/{id}/hide")
    public ResponseEntity<Void> hideUser(@PathVariable Long id) {
        adminService.hideUser(id);
        return ResponseEntity.ok().build();
    }

    // 리스트 비노출
    @Operation(summary = "리스트 비노출")
    @PatchMapping("/lists/{id}/hide")
    public ResponseEntity<Void> hideList(@PathVariable Long id) {
        adminService.hideList(id);
        return ResponseEntity.ok().build();
    }
}
