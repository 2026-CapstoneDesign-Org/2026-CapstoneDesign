package com.example.Capstone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.AdditionalInfoRequest;
import com.example.Capstone.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final UserService userService;

    @Operation(summary = "소셜 로그인 또는 가입 처리")
    @ApiResponse(responseCode = "200", description = "로그인 성공 - JWT 반환")
    @ApiResponse(responseCode = "302", description = "추가 정보 입력 필요 시 /auth/signup/profile 로 리디렉션")
    @PostMapping("/oauth/{provider}")
    public ResponseEntity<Void> oauth(@PathVariable String provider) {
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "최초 프로필 입력", description = "소셜 로그인 후 생년월일, 성별 입력")
    @ApiResponse(responseCode = "200", description = "프로필 입력 성공")
    @PostMapping(value = "/signup/profile", consumes = "application/json")
    public ResponseEntity<Void> signupProfile(
        @AuthenticationPrincipal Long userId, 
        @RequestBody @Valid AdditionalInfoRequest request) {
        userService.updateAdditionalInfo(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/success")
    @Operation(summary = "소셜 로그인 성공 테스트 - 프론트 구현 후 삭제")
    public ResponseEntity<String> success(@RequestParam String accessToken) {
        return ResponseEntity.ok(accessToken);
    }

}
