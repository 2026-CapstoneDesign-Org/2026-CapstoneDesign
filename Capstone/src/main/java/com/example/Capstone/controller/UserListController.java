package com.example.Capstone.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.AddExternalRestaurantRequest;
import com.example.Capstone.dto.request.AddRestaurantRequest;
import com.example.Capstone.dto.request.CreateListRequest;
import com.example.Capstone.dto.request.UpdateListRequest;
import com.example.Capstone.dto.request.UpdateScoreRequest;
import com.example.Capstone.dto.response.UserListDetailResponse;
import com.example.Capstone.dto.response.UserListResponse;
import com.example.Capstone.service.UserListService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/lists")
@RequiredArgsConstructor
@Tag(name = "List", description = "리스트 API")
public class UserListController {
    
    private final UserListService userListService;

    @Operation(summary = "리스트 생성")
    @PostMapping
    public ResponseEntity<UserListResponse> createList(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateListRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userListService.createList(userId, request));
    }

    @Operation(summary = "내 리스트 목록")
    @GetMapping
    public ResponseEntity<List<UserListResponse>> getMyLists(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userListService.getMyLists(userId));
    }

    @Operation(summary = "리스트 상세")
    @GetMapping("/{id}")
    public ResponseEntity<UserListDetailResponse> getList(
            @PathVariable Long id) {
        return ResponseEntity.ok(userListService.getList(id));
    }

    @Operation(summary = "리스트 기본 정보 수정")
    @PatchMapping("/{id}")
    public ResponseEntity<UserListResponse> updateList(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid UpdateListRequest request) {
        return ResponseEntity.ok(userListService.updateList(userId, id, request));
    }

    @Operation(summary = "공개 / 비공개 변경")
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> toggleVisibility(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        userListService.toggleVisibility(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "대표 리스트 지정")
    @PatchMapping("/{id}/representative")
    public ResponseEntity<Void> setRepresentative(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        userListService.setRepresentative(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리스트 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteList(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        userListService.deleteList(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리스트에 식당 추가")
    @PostMapping("/{id}/restaurants")
    public ResponseEntity<Void> addRestaurant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid AddRestaurantRequest request) {
        userListService.addRestaurant(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "외부 검색 결과 식당을 리스트에 추가")
    @PostMapping("/{id}/restaurants/external-fallback")
    public ResponseEntity<Void> addExternalFallbackRestaurant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody @Valid AddExternalRestaurantRequest request) {
        userListService.addExternalFallbackRestaurant(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "평가 수정")
    @PatchMapping("/{id}/restaurants/{restaurantId}")
    public ResponseEntity<Void> updateScore(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long restaurantId,
            @RequestBody @Valid UpdateScoreRequest request) {
        userListService.updateScore(userId, id, restaurantId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리스트에서 식당 삭제")
    @DeleteMapping("/{id}/restaurants/{restaurantId}")
    public ResponseEntity<Void> removeRestaurant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long restaurantId) {
        userListService.removeRestaurant(userId, id, restaurantId);
        return ResponseEntity.noContent().build();
    }
}
