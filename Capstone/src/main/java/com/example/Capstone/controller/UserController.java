package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.UpdateUserRequest;
import com.example.Capstone.dto.response.UserResponse;
import com.example.Capstone.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 API")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMyInfo(userId));
    }

    @Operation(summary = "닉네임 / 이미지 수정")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @PatchMapping("/me")
    public ResponseEntity<Void> updateUser(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        userService.updateUser(userId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원 탈퇴", description = "Soft Delete 처리")
    @ApiResponse(responseCode = "204", description = "탈퇴 성공")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
